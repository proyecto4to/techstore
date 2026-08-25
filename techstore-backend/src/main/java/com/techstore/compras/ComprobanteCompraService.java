package com.techstore.compras;

import com.techstore.comun.BusinessException;
import com.techstore.comun.CondicionOperacion;
import com.techstore.comun.DesgloseIva;
import com.techstore.comun.Dinero;
import com.techstore.comun.Moneda;
import com.techstore.comun.NumeroComprobante;
import com.techstore.comun.ResourceNotFoundException;
import com.techstore.comun.RucParaguayo;
import com.techstore.comun.TipoComprobante;
import com.techstore.comun.TipoIdentificacion;
import com.techstore.ventas.EstadoPedido;
import com.techstore.ventas.Pedido;
import com.techstore.ventas.PedidoRepository;
import com.techstore.ventas.TipoPedido;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * Carga y validación del comprobante fiscal que emitió el proveedor.
 *
 * <p>Las reglas de qué campos exige cada tipo de documento las sabe
 * {@link TipoComprobante}, porque son del comprobante y no del archivo que se
 * le manda a la DNIT: una factura sin número está mal cargada hoy, no el día
 * que se exporte el período.
 *
 * <p>Se valida acá y no solo al exportar por eso mismo: quien tiene el papel a
 * la vista es la persona que lo carga. Descubrir el mes siguiente que faltaba
 * un timbrado significa buscar de nuevo un documento que ya se archivó.
 */
@Service
@RequiredArgsConstructor
public class ComprobanteCompraService {

    private static final String TIMBRADO = "\\d{1,8}";
    private static final Map<String, String> EXTENSIONES_ARCHIVO = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "application/pdf", ".pdf");

    private final CompraComprobanteRepository comprobanteRepository;
    private final PedidoRepository pedidoRepository;

    @Value("${app.uploads.dir}")
    private String uploadsDir;

    /**
     * Asienta el comprobante de una compra, o reemplaza el que ya tenía. Se
     * puede cargar antes o después de recibir la mercadería: la factura del
     * proveedor a veces viene con el pedido y a veces llega después.
     */
    @Transactional
    public ComprobanteCompraResponse registrar(Long compraId, ComprobanteCompraRequest request) {
        Pedido compra = buscarCompra(compraId);
        if (compra.getEstado() == EstadoPedido.X) {
            throw new BusinessException("compras.comprobante.compra.anulada", compra.getNumero());
        }

        TipoComprobante tipo = tipo(request.tipoComprobante());
        if (!tipo.sirveParaCompras()) {
            throw new BusinessException("compras.comprobante.tipo.no.es.de.compra", tipo.etiqueta());
        }

        CompraComprobante comprobante = comprobanteRepository.findByPedidoId(compraId)
                .orElseGet(CompraComprobante::new);
        comprobante.setPedido(compra);
        comprobante.setTipoComprobante(tipo);
        comprobante.setFechaEmision(fecha(request.fechaEmision()));
        comprobante.setCondicion(request.condicion() == null ? CondicionOperacion.Contado : request.condicion());

        String timbrado = timbrado(tipo, request.timbrado());
        String numero = numero(tipo, request.numero());
        exigirQueNoEsteCargado(timbrado, numero, comprobante);
        comprobante.setTimbrado(timbrado);
        comprobante.setNumero(numero);

        aplicarAsociado(comprobante, tipo, request);
        aplicarProveedor(comprobante, compra);
        aplicarImportes(comprobante, tipo, compra, request);

        return ComprobanteCompraResponse.from(comprobanteRepository.save(comprobante));
    }

    @Transactional(readOnly = true)
    public ComprobanteCompraResponse obtener(Long compraId) {
        return comprobanteRepository.findByPedidoId(compraId)
                .map(ComprobanteCompraResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("compras.comprobante.no.cargado", compraId));
    }

    /** Guarda una copia autenticada del papel; admite cámara/galería y PDF. */
    @Transactional
    public ComprobanteCompraResponse subirArchivo(Long compraId, MultipartFile archivo) {
        CompraComprobante comprobante = comprobanteRepository.findByPedidoId(compraId)
                .orElseThrow(() -> new ResourceNotFoundException("compras.comprobante.no.cargado", compraId));
        if (archivo == null || archivo.isEmpty()) {
            throw new BusinessException("compras.comprobante.archivo.vacio");
        }
        String contentType = archivo.getContentType();
        String extension = EXTENSIONES_ARCHIVO.get(contentType);
        if (extension == null) {
            throw new BusinessException("compras.comprobante.archivo.formato");
        }
        try {
            byte[] contenido = archivo.getBytes();
            if (!firmaCoincide(contentType, contenido)) {
                throw new BusinessException("compras.comprobante.archivo.formato");
            }
            Path carpeta = carpetaArchivos();
            Files.createDirectories(carpeta);
            String nombre = "comprobante-" + comprobante.getId() + "-" + UUID.randomUUID() + extension;
            Files.write(carpeta.resolve(nombre), contenido, StandardOpenOption.CREATE_NEW);
            String anterior = comprobante.getArchivoNombre();
            comprobante.setArchivoNombre(nombre);
            comprobante.setArchivoContentType(contentType);
            ComprobanteCompraResponse response = ComprobanteCompraResponse.from(
                    comprobanteRepository.save(comprobante));
            borrarAnterior(anterior, carpeta);
            return response;
        } catch (IOException ex) {
            throw new BusinessException("compras.comprobante.archivo.no.guardado", ex.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public ArchivoComprobante obtenerArchivo(Long compraId) {
        CompraComprobante comprobante = comprobanteRepository.findByPedidoId(compraId)
                .filter(item -> item.getArchivoNombre() != null)
                .orElseThrow(() -> new ResourceNotFoundException("compras.comprobante.archivo.no.encontrado", compraId));
        Path carpeta = carpetaArchivos();
        Path archivo = carpeta.resolve(comprobante.getArchivoNombre()).normalize();
        if (!archivo.startsWith(carpeta) || !Files.isRegularFile(archivo)) {
            throw new ResourceNotFoundException("compras.comprobante.archivo.no.encontrado", compraId);
        }
        try {
            return new ArchivoComprobante(
                    Files.readAllBytes(archivo),
                    comprobante.getArchivoContentType(),
                    comprobante.getArchivoNombre());
        } catch (IOException ex) {
            throw new BusinessException("compras.comprobante.archivo.no.leido", ex.getMessage());
        }
    }

    private Path carpetaArchivos() {
        return Paths.get(uploadsDir, "comprobantes-compra").toAbsolutePath().normalize();
    }

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

    private static void borrarAnterior(String nombre, Path carpeta) {
        if (nombre == null) return;
        Path candidato = carpeta.resolve(nombre).normalize();
        if (!candidato.startsWith(carpeta)) return;
        try {
            Files.deleteIfExists(candidato);
        } catch (IOException ignored) {
            // El archivo nuevo ya quedó asociado; el anterior puede limpiarse luego.
        }
    }

    public record ArchivoComprobante(byte[] contenido, String contentType, String nombre) {}

    private Pedido buscarCompra(Long id) {
        Pedido pedido = pedidoRepository.findWithDetallesById(id)
                .orElseThrow(() -> new ResourceNotFoundException("compras.no.encontrada", id));
        if (pedido.getTipo() != TipoPedido.Compra) {
            throw new BusinessException("compras.pedido.no.es.compra", pedido.getNumero());
        }
        return pedido;
    }

    private static TipoComprobante tipo(Integer codigo) {
        if (codigo == null) {
            return TipoComprobante.Factura;
        }
        try {
            return TipoComprobante.porCodigo(codigo);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("compras.comprobante.tipo.desconocido", codigo);
        }
    }

    private static LocalDate fecha(LocalDate fecha) {
        // Una fecha futura es un error de tipeo, y arrastraría el comprobante al
        // período equivocado. El límite hacia atrás no se pone acá: cargar una
        // factura vieja para tener el historial completo es legítimo, y si no se
        // puede declarar lo avisa la exportación.
        if (fecha.isAfter(LocalDate.now())) {
            throw new BusinessException("compras.comprobante.fecha.futura", fecha);
        }
        return fecha;
    }

    private static String timbrado(TipoComprobante tipo, String valor) {
        String limpio = limpio(valor);
        if (!tipo.exigeTimbrado()) {
            // El despacho de importación no tiene timbrado; se declara con cero.
            return null;
        }
        if (limpio == null) {
            throw new BusinessException("compras.comprobante.timbrado.obligatorio", tipo.etiqueta());
        }
        if (!limpio.matches(TIMBRADO)) {
            throw new BusinessException("compras.comprobante.timbrado.formato", limpio);
        }
        return limpio;
    }

    private static String numero(TipoComprobante tipo, String valor) {
        String normalizado = NumeroComprobante.normalizar(valor);
        if (!tipo.exigeNumero()) {
            return null;
        }
        if (normalizado == null) {
            throw new BusinessException("compras.comprobante.numero.obligatorio", tipo.etiqueta());
        }
        if (!NumeroComprobante.esValido(normalizado)) {
            throw new BusinessException("compras.comprobante.numero.formato", normalizado);
        }
        return normalizado;
    }

    /**
     * Un timbrado pertenece a un solo contribuyente, así que timbrado + número
     * es único en todo el país. Cargarlo dos veces duplicaría el crédito fiscal.
     */
    private void exigirQueNoEsteCargado(String timbrado, String numero, CompraComprobante propio) {
        if (timbrado == null || numero == null) {
            return;
        }
        comprobanteRepository.findByTimbradoAndNumero(timbrado, numero)
                .filter(otro -> !otro.getId().equals(propio.getId()))
                .ifPresent(otro -> {
                    throw new BusinessException("compras.comprobante.duplicado",
                            numero, otro.getPedido().getNumero());
                });
    }

    private static void aplicarAsociado(CompraComprobante comprobante, TipoComprobante tipo,
                                        ComprobanteCompraRequest request) {
        if (!tipo.exigeAsociado()) {
            comprobante.setAsociadoNumero(null);
            comprobante.setAsociadoTimbrado(null);
            return;
        }
        String numero = NumeroComprobante.normalizar(request.asociadoNumero());
        String timbrado = limpio(request.asociadoTimbrado());
        if (numero == null || timbrado == null) {
            throw new BusinessException("compras.comprobante.asociado.obligatorio", tipo.etiqueta());
        }
        if (!NumeroComprobante.esValido(numero)) {
            throw new BusinessException("compras.comprobante.numero.formato", numero);
        }
        if (!timbrado.matches(TIMBRADO)) {
            throw new BusinessException("compras.comprobante.timbrado.formato", timbrado);
        }
        comprobante.setAsociadoNumero(numero);
        comprobante.setAsociadoTimbrado(timbrado);
    }

    /**
     * Congela quién emitió el documento. La DNIT identifica al proveedor por
     * RUC; la autofactura y el despacho de importación usan otros tipos de
     * identificación, pero el sistema no los captura todavía y sus proveedores
     * son contribuyentes con RUC.
     */
    private static void aplicarProveedor(CompraComprobante comprobante, Pedido compra) {
        Proveedor proveedor = compra.getProveedor();
        String ruc = proveedor == null ? null : RucParaguayo.normalizar(proveedor.getRuc());
        if (ruc == null) {
            throw new BusinessException("compras.comprobante.proveedor.sin.ruc",
                    proveedor == null ? "" : proveedor.getRazonSocial());
        }
        String motivo = RucParaguayo.motivoInvalidez(ruc);
        if (motivo != null) {
            throw new BusinessException("facturacion.ruc.invalido", ruc, motivo);
        }
        comprobante.setTipoIdentificacion(TipoIdentificacion.RUC);
        comprobante.setProveedorRuc(ruc);
        comprobante.setProveedorRazonSocial(proveedor.getRazonSocial());
    }

    private static void aplicarImportes(CompraComprobante comprobante, TipoComprobante tipo,
                                        Pedido compra, ComprobanteCompraRequest request) {
        Moneda moneda = compra.getMoneda();
        comprobante.setMoneda(moneda);

        Dinero gravado10 = Dinero.cero(moneda);
        Dinero gravado5 = Dinero.cero(moneda);
        Dinero exento = Dinero.cero(moneda);
        Dinero total;

        if (!tipo.llevaDesglose()) {
            // Autofactura, boleta RESIMPLE, lotería y ticket: no dan crédito
            // fiscal, así que la DNIT quiere el total y nada más.
            total = request.total() == null ? compra.getTotal() : Dinero.de(request.total(), moneda);
        } else if (request.traeImportes()) {
            gravado10 = Dinero.deONulo(request.gravado10(), moneda);
            gravado5 = Dinero.deONulo(request.gravado5(), moneda);
            exento = Dinero.deONulo(request.exento(), moneda);
            Dinero suma = gravado10.mas(gravado5).mas(exento);
            total = request.total() == null ? suma : Dinero.de(request.total(), moneda);
            if (total.compareTo(suma) != 0) {
                throw new BusinessException("compras.comprobante.desglose.no.cuadra",
                        suma.plano(), total.plano());
            }
        } else {
            // Sin importes cargados vale lo que dice la compra: es el caso normal,
            // el proveedor facturó lo que se le pidió.
            DesgloseIva desglose = DesgloseIva.de(moneda, compra.getDetalles());
            gravado10 = desglose.gravado10();
            gravado5 = desglose.gravado5();
            exento = desglose.exento();
            total = desglose.total();
        }

        if (!total.esPositivo()) {
            throw new BusinessException("compras.comprobante.total.positivo");
        }

        comprobante.setGravado10(gravado10);
        comprobante.setGravado5(gravado5);
        comprobante.setExento(exento);
        comprobante.setTotal(total);
    }

    private static String limpio(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
