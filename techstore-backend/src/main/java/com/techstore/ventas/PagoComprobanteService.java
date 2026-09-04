package com.techstore.ventas;

import com.techstore.comun.BusinessException;
import com.techstore.comun.ResourceNotFoundException;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Los comprobantes con los que se respalda el pago de un pedido.
 *
 * <p>El tope de tres, los formatos aceptados y el tamaño se validan acá y no
 * solo en la pantalla: la app puede deshabilitar el botón al llegar al tercero,
 * pero eso no impide mandar un cuarto por la API.
 *
 * <p>Se comprueba la firma de los primeros bytes además del {@code contentType}
 * declarado, porque ese encabezado lo pone quien sube el archivo y se puede
 * decir cualquier cosa.
 */
@Service
@RequiredArgsConstructor
public class PagoComprobanteService {

    /** Cuántos respaldos admite un pedido. */
    public static final int MAXIMO_POR_PEDIDO = 3;

    /** 5 MB, el mismo límite que acepta el servidor para una subida. */
    private static final long MAXIMO_BYTES = 5L * 1024 * 1024;

    private static final Map<String, String> EXTENSIONES = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "application/pdf", ".pdf");

    private final PagoComprobanteRepository comprobanteRepository;
    private final PedidoRepository pedidoRepository;
    private final UsuarioRepository usuarioRepository;

    @Value("${app.uploads.dir}")
    private String uploadsDir;

    @Transactional(readOnly = true)
    public List<PagoComprobanteResponse> listar(Long pedidoId, String email, boolean esStaff) {
        autorizar(pedidoId, email, esStaff);
        return comprobanteRepository.findByPedidoIdOrderByCreatedAtAsc(pedidoId).stream()
                .map(PagoComprobanteResponse::from)
                .toList();
    }

    @Transactional
    public PagoComprobanteResponse adjuntar(
            Long pedidoId, MultipartFile archivo, String email, boolean esStaff) {
        Pedido pedido = autorizar(pedidoId, email, esStaff);
        if (pedido.getEstado() == EstadoPedido.X) {
            throw new BusinessException("pagos.comprobante.pedido.cancelado", pedido.getNumero());
        }
        if (comprobanteRepository.countByPedidoId(pedidoId) >= MAXIMO_POR_PEDIDO) {
            throw new BusinessException("pagos.comprobante.tope", MAXIMO_POR_PEDIDO);
        }
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
            if (!firmaCoincide(archivo.getContentType(), contenido)) {
                throw new BusinessException("pagos.comprobante.formato");
            }
            Path carpeta = carpeta();
            Files.createDirectories(carpeta);
            String nombre = "pago-" + pedidoId + "-" + UUID.randomUUID() + extension;
            Files.write(carpeta.resolve(nombre), contenido, StandardOpenOption.CREATE_NEW);

            PagoComprobante comprobante = new PagoComprobante();
            comprobante.setPedido(pedido);
            comprobante.setArchivoNombre(nombre);
            comprobante.setArchivoContentType(archivo.getContentType());
            comprobante.setTamanoBytes((long) contenido.length);
            comprobante.setSubidoPor(usuario(email));
            return PagoComprobanteResponse.from(comprobanteRepository.save(comprobante));
        } catch (IOException error) {
            throw new BusinessException("pagos.comprobante.guardado");
        }
    }

    @Transactional
    public void eliminar(Long pedidoId, Long comprobanteId, String email, boolean esStaff) {
        autorizar(pedidoId, email, esStaff);
        PagoComprobante comprobante = comprobanteRepository
                .findByIdAndPedidoId(comprobanteId, pedidoId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "pagos.comprobante.no.encontrado", comprobanteId));
        borrarArchivo(comprobante.getArchivoNombre());
        comprobanteRepository.delete(comprobante);
    }

    @Transactional(readOnly = true)
    public ArchivoAdjunto obtenerArchivo(
            Long pedidoId, Long comprobanteId, String email, boolean esStaff) {
        autorizar(pedidoId, email, esStaff);
        PagoComprobante comprobante = comprobanteRepository
                .findByIdAndPedidoId(comprobanteId, pedidoId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "pagos.comprobante.no.encontrado", comprobanteId));
        Path archivo = carpeta().resolve(comprobante.getArchivoNombre()).normalize();
        if (!archivo.startsWith(carpeta())) {
            throw new ResourceNotFoundException("pagos.comprobante.no.encontrado", comprobanteId);
        }
        try {
            return new ArchivoAdjunto(
                    Files.readAllBytes(archivo), comprobante.getArchivoContentType());
        } catch (IOException error) {
            throw new ResourceNotFoundException("pagos.comprobante.no.encontrado", comprobanteId);
        }
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

    /**
     * Los primeros bytes tienen que corresponder al tipo declarado.
     *
     * <p>El {@code contentType} lo manda quien sube: sin esta comprobación,
     * cualquier archivo puede decir que es una imagen.
     */
    private static boolean firmaCoincide(String contentType, byte[] bytes) {
        return switch (contentType) {
            case "image/jpeg" -> bytes.length >= 3
                    && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8 && (bytes[2] & 0xff) == 0xff;
            case "image/png" -> bytes.length >= 8
                    && (bytes[0] & 0xff) == 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4e && bytes[3] == 0x47
                    && bytes[4] == 0x0d && bytes[5] == 0x0a && bytes[6] == 0x1a && bytes[7] == 0x0a;
            case "application/pdf" -> bytes.length >= 5
                    && bytes[0] == '%' && bytes[1] == 'P' && bytes[2] == 'D' && bytes[3] == 'F' && bytes[4] == '-';
            default -> false;
        };
    }

    /** Un adjunto listo para devolver por HTTP. */
    public record ArchivoAdjunto(byte[] contenido, String contentType) { }
}
