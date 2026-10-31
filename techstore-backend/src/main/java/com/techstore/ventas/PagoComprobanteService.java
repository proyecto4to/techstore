package com.techstore.ventas;

import com.techstore.comun.BusinessException;
import com.techstore.comun.ResourceNotFoundException;
import com.techstore.notificaciones.outbox.NotificationOutboxPublisher;
import com.techstore.usuarios.Usuario;
import com.techstore.usuarios.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Los comprobantes con los que se respalda el pago de un pedido.
 *
 * <p>El tope, los formatos aceptados y el tamaño se validan acá y no solo en
 * la pantalla: la app puede deshabilitar el botón al llegar al tercero, pero
 * eso no impide mandar un cuarto por la API.
 *
 * <p>Un comprobante puede llegar de dos maneras. Lo normal es que el cliente
 * lo suba durante el checkout, antes de que exista el pedido: queda «suelto»,
 * solo de quien lo subió, y el checkout lo engancha al pedido que crea. La otra
 * es adjuntarlo a un pedido que ya existe, que es lo que pasa cuando la tienda
 * rechazó el primero y el cliente manda otro.
 *
 * <p>Adjuntar no es pagar: el comprobante deja el pago en verificación, y lo
 * que lo da por cobrado es la aprobación de la tienda
 * ({@link VerificacionPagoService}).
 */
@Service
@RequiredArgsConstructor
public class PagoComprobanteService {

    /** Cuántos respaldos pueden estar en revisión a la vez en un pedido. */
    public static final int MAXIMO_POR_PEDIDO = 3;

    /**
     * Cuántos admite un pedido contando los rechazados, que se guardan como
     * historial. Sin este techo, rechazar y volver a mandar no terminaría nunca.
     */
    static final int MAXIMO_HISTORICO_POR_PEDIDO = 12;

    /** Cuántos sueltos puede tener alguien esperando un checkout. */
    static final int MAXIMO_SUELTOS_POR_USUARIO = 6;

    /** 5 MB, el mismo límite que acepta el servidor para una subida. */
    private static final long MAXIMO_BYTES = 5L * 1024 * 1024;

    private static final Map<String, String> EXTENSIONES = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "application/pdf", ".pdf");

    private static final Set<String> ROLES_QUE_COBRAN = Set.of("Admin", "Cajero");

    private final PagoComprobanteRepository comprobanteRepository;
    private final PedidoRepository pedidoRepository;
    private final UsuarioRepository usuarioRepository;
    private final PedidoHistorial historial;
    private final NotificationOutboxPublisher notificaciones;

    @Value("${app.uploads.dir}")
    private String uploadsDir;

    @Transactional(readOnly = true)
    public List<PagoComprobanteResponse> listar(Long pedidoId, String email, boolean esStaff) {
        autorizar(pedidoId, email, esStaff);
        return comprobanteRepository.findByPedidoIdOrderByCreatedAtAsc(pedidoId).stream()
                .map(PagoComprobanteResponse::from)
                .toList();
    }

    /**
     * Sube un comprobante antes del checkout.
     *
     * <p>Queda sin pedido y solo lo puede ver, quitar o usar quien lo subió.
     * Si nunca se usa, la limpieza diaria lo borra.
     */
    @Transactional
    public PagoComprobanteResponse subirSuelto(MultipartFile archivo, String email) {
        Usuario usuario = usuario(email);
        if (comprobanteRepository.contarSueltosDe(usuario.getId()) >= MAXIMO_SUELTOS_POR_USUARIO) {
            throw new BusinessException("pagos.comprobante.sueltos.tope");
        }
        PagoComprobante comprobante = guardarArchivo(archivo, "pago-checkout-" + usuario.getId());
        comprobante.setSubidoPor(usuario);
        return PagoComprobanteResponse.from(comprobanteRepository.save(comprobante));
    }

    @Transactional
    public void quitarSuelto(Long comprobanteId, String email) {
        PagoComprobante comprobante = sueltoPropio(comprobanteId, email);
        borrarArchivo(comprobante.getArchivoNombre());
        comprobanteRepository.delete(comprobante);
    }

    @Transactional(readOnly = true)
    public ArchivoAdjunto archivoSuelto(Long comprobanteId, String email) {
        return leer(sueltoPropio(comprobanteId, email));
    }

    /**
     * Engancha al pedido los comprobantes que el cliente subió en el checkout.
     *
     * <p>Se bloquean antes de mirarlos: dos compras simultáneas con el mismo
     * respaldo no pueden quedarse las dos con él. Cada uno tiene que ser de quien
     * compra y estar todavía suelto; si alguno no lo está, no se usa ninguno.
     *
     * @return cuántos quedaron enganchados
     */
    @Transactional
    public int vincular(Pedido pedido, List<Long> ids, Usuario comprador) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        Set<Long> unicos = new LinkedHashSet<>(ids);
        if (unicos.size() > MAXIMO_POR_PEDIDO) {
            throw new BusinessException("pagos.comprobante.tope", MAXIMO_POR_PEDIDO);
        }
        List<PagoComprobante> comprobantes = comprobanteRepository.findAllForUpdate(unicos);
        if (comprobantes.size() != unicos.size()) {
            throw new BusinessException("pagos.comprobante.checkout.invalido");
        }
        for (PagoComprobante comprobante : comprobantes) {
            boolean propio = comprobante.getSubidoPor() != null
                    && comprobante.getSubidoPor().getId().equals(comprador.getId());
            if (!propio || comprobante.getPedido() != null) {
                throw new BusinessException("pagos.comprobante.checkout.invalido");
            }
            comprobante.setPedido(pedido);
            comprobante.setEstado(EstadoComprobante.EN_REVISION);
        }
        comprobanteRepository.saveAll(comprobantes);
        return comprobantes.size();
    }

    /**
     * Adjunta un comprobante a un pedido que ya existe.
     *
     * <p>Es el camino para volver a mandar el respaldo después de un rechazo, o
     * para quien eligió transferencia en una versión anterior de la app y no
     * pudo subirlo en el checkout. Deja el pago en verificación y avisa a quienes
     * cobran.
     */
    @Transactional
    public PagoComprobanteResponse adjuntar(
            Long pedidoId, MultipartFile archivo, String email, boolean esStaff) {
        Pedido pedido = autorizar(pedidoId, email, esStaff);
        if (pedido.getEstado() == EstadoPedido.X) {
            throw new BusinessException("pagos.comprobante.pedido.cancelado", pedido.getNumero());
        }
        if (pedido.getMetodoPago() != MetodoPagoCheckout.TRANSFERENCIA_BANCARIA) {
            throw new BusinessException("pagos.comprobante.no.transferencia", pedido.getNumero());
        }
        if (pedido.getEstadoPago() == EstadoPago.APROBADO) {
            throw new BusinessException("pagos.comprobante.ya.aprobado", pedido.getNumero());
        }
        if (comprobanteRepository.countByPedidoIdAndEstado(pedidoId, EstadoComprobante.EN_REVISION)
                >= MAXIMO_POR_PEDIDO) {
            throw new BusinessException("pagos.comprobante.tope", MAXIMO_POR_PEDIDO);
        }
        if (comprobanteRepository.countByPedidoId(pedidoId) >= MAXIMO_HISTORICO_POR_PEDIDO) {
            throw new BusinessException("pagos.comprobante.historico.tope");
        }
        Usuario autor = usuario(email);
        PagoComprobante comprobante = guardarArchivo(archivo, "pago-" + pedidoId);
        comprobante.setPedido(pedido);
        comprobante.setSubidoPor(autor);
        PagoComprobante guardado = comprobanteRepository.save(comprobante);

        historial.registrar(pedido, TipoEventoPedido.COMPROBANTE_ADJUNTO,
                "Se adjuntó un comprobante de la transferencia", autor);
        if (pedido.getEstadoPago() != EstadoPago.EN_VERIFICACION) {
            pedido.setEstadoPago(EstadoPago.EN_VERIFICACION);
            pedido.setPagoRechazoMotivo(null);
            pedidoRepository.save(pedido);
        }
        notificaciones.pagoEnVerificacion(pedido, guardado.getId(), personalQueCobra(), true);
        return PagoComprobanteResponse.from(guardado);
    }

    /**
     * Quita un comprobante que todavía nadie revisó.
     *
     * <p>Los que ya se revisaron no se borran: el aprobado respalda una factura,
     * y el rechazado explica por qué se pidió otro.
     */
    @Transactional
    public void eliminar(Long pedidoId, Long comprobanteId, String email, boolean esStaff) {
        Pedido pedido = autorizar(pedidoId, email, esStaff);
        PagoComprobante comprobante = comprobanteRepository
                .findByIdAndPedidoId(comprobanteId, pedidoId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "pagos.comprobante.no.encontrado", comprobanteId));
        if (comprobante.getEstado() != EstadoComprobante.EN_REVISION
                || pedido.getEstadoPago() == EstadoPago.APROBADO) {
            throw new BusinessException("pagos.comprobante.revisado");
        }
        borrarArchivo(comprobante.getArchivoNombre());
        comprobanteRepository.delete(comprobante);
        comprobanteRepository.flush();
        historial.registrar(pedido, TipoEventoPedido.COMPROBANTE_QUITADO,
                "Se quitó un comprobante antes de revisarlo", usuario(email));
        // Sin ninguno en revisión, ya no hay nada que verificar: el pedido vuelve
        // a esperar el respaldo del cliente.
        if (pedido.getEstadoPago() == EstadoPago.EN_VERIFICACION
                && comprobanteRepository.countByPedidoIdAndEstado(pedidoId, EstadoComprobante.EN_REVISION) == 0) {
            pedido.setEstadoPago(EstadoPago.PENDIENTE_COMPROBANTE);
            pedidoRepository.save(pedido);
        }
    }

    @Transactional(readOnly = true)
    public ArchivoAdjunto obtenerArchivo(
            Long pedidoId, Long comprobanteId, String email, boolean esStaff) {
        autorizar(pedidoId, email, esStaff);
        PagoComprobante comprobante = comprobanteRepository
                .findByIdAndPedidoId(comprobanteId, pedidoId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "pagos.comprobante.no.encontrado", comprobanteId));
        return leer(comprobante);
    }

    /**
     * Borra los comprobantes que se subieron para un checkout que nunca se hizo.
     *
     * <p>Un día alcanza para terminar una compra; pasado eso, el archivo es una
     * foto de una transferencia ajena guardada sin motivo.
     *
     * @return cuántos se borraron
     */
    @Transactional
    public int limpiarSueltos() {
        List<PagoComprobante> viejos =
                comprobanteRepository.findSueltosAnterioresA(LocalDateTime.now().minusDays(1));
        for (PagoComprobante comprobante : viejos) {
            borrarArchivo(comprobante.getArchivoNombre());
        }
        comprobanteRepository.deleteAll(viejos);
        return viejos.size();
    }

    /** Ids de quienes cobran, para avisarles que hay un pago para revisar. */
    List<Long> personalQueCobra() {
        return usuarioRepository.findByRolNombreInAndActivoTrue(ROLES_QUE_COBRAN).stream()
                .map(Usuario::getId)
                .toList();
    }

    private PagoComprobante guardarArchivo(MultipartFile archivo, String prefijo) {
        if (archivo == null || archivo.isEmpty()) {
            throw new BusinessException("pagos.comprobante.vacio");
        }
        if (archivo.getSize() > MAXIMO_BYTES) {
            throw new BusinessException("pagos.comprobante.tamano");
        }
        String extension = EXTENSIONES.get(archivo.getContentType());
        if (extension == null) {
            throw new BusinessException("pagos.comprobante.formato");
        }
        try {
            byte[] contenido = archivo.getBytes();
            if (!FirmaDeArchivo.coincide(archivo.getContentType(), contenido)) {
                throw new BusinessException("pagos.comprobante.formato");
            }
            Path carpeta = carpeta();
            Files.createDirectories(carpeta);
            String nombre = prefijo + "-" + UUID.randomUUID() + extension;
            Files.write(carpeta.resolve(nombre), contenido, StandardOpenOption.CREATE_NEW);
            PagoComprobante comprobante = new PagoComprobante();
            comprobante.setArchivoNombre(nombre);
            comprobante.setArchivoContentType(archivo.getContentType());
            comprobante.setTamanoBytes((long) contenido.length);
            comprobante.setEstado(EstadoComprobante.EN_REVISION);
            return comprobante;
        } catch (IOException error) {
            throw new BusinessException("pagos.comprobante.guardado");
        }
    }

    private ArchivoAdjunto leer(PagoComprobante comprobante) {
        Path archivo = carpeta().resolve(comprobante.getArchivoNombre()).normalize();
        if (!archivo.startsWith(carpeta())) {
            throw new ResourceNotFoundException("pagos.comprobante.no.encontrado", comprobante.getId());
        }
        try {
            return new ArchivoAdjunto(
                    Files.readAllBytes(archivo), comprobante.getArchivoContentType());
        } catch (IOException error) {
            throw new ResourceNotFoundException("pagos.comprobante.no.encontrado", comprobante.getId());
        }
    }

    private PagoComprobante sueltoPropio(Long comprobanteId, String email) {
        Usuario usuario = usuario(email);
        PagoComprobante comprobante = comprobanteRepository.findSuelto(comprobanteId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "pagos.comprobante.no.encontrado", comprobanteId));
        if (comprobante.getSubidoPor() == null
                || !comprobante.getSubidoPor().getId().equals(usuario.getId())) {
            // Mismo mensaje que si no existiera: decir «es de otro» confirma que el id existe.
            throw new ResourceNotFoundException("pagos.comprobante.no.encontrado", comprobanteId);
        }
        return comprobante;
    }

    /** El pedido, si quien pregunta tiene por qué verlo. */
    private Pedido autorizar(Long pedidoId, String email, boolean esStaff) {
        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new ResourceNotFoundException("ventas.pedido.no.encontrado", pedidoId));
        if (!esStaff && !pedido.getUsuario().getEmail().equalsIgnoreCase(email)) {
            throw new AccessDeniedException("No puede ver comprobantes de otro usuario");
        }
        return pedido;
    }

    private Usuario usuario(String email) {
        return usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("Usuario no encontrado"));
    }

    private Path carpeta() {
        return Paths.get(uploadsDir, "comprobantes-pago").toAbsolutePath().normalize();
    }

    private void borrarArchivo(String nombre) {
        Path candidato = carpeta().resolve(nombre).normalize();
        if (!candidato.startsWith(carpeta())) {
            return;
        }
        try {
            Files.deleteIfExists(candidato);
        } catch (IOException ignorado) {
            // El registro ya se va de la base; un archivo huérfano se limpia después.
        }
    }

    /** Un adjunto listo para devolver por HTTP. */
    public record ArchivoAdjunto(byte[] contenido, String contentType) { }
}
