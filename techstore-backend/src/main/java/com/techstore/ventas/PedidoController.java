package com.techstore.ventas;

import com.techstore.comun.PageResponse;
import com.techstore.facturacion.PagoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/pedidos")
@RequiredArgsConstructor
@Tag(name = "Pedidos", description = "Checkout y gestión de pedidos de venta")
public class PedidoController {

    private final PedidoService pedidoService;
    private final PagoService pagoService;
    private final PagoComprobanteService comprobanteService;
    private final VerificacionPagoService verificacionService;

    @GetMapping("/{id}/historial")
    @Operation(summary = "Línea de tiempo del pedido",
            description = "Creación, comprobantes, verificación del pago, confirmación y cancelación")
    public List<PedidoEventoResponse> historial(@PathVariable Long id, Authentication auth) {
        return pedidoService.historial(id, auth.getName(), esStaff(auth));
    }

    @PutMapping("/{id}/pago/aprobar")
    @PreAuthorize("hasAnyRole('Admin','Cajero')")
    @Operation(summary = "Aprobar la transferencia (Admin/Cajero)",
            description = "Confirma el pedido, registra el cobro por transferencia, emite la factura "
                    + "y pone el envío en preparación, todo junto")
    public PedidoResponse aprobarPago(@PathVariable Long id,
                                      @Valid @RequestBody(required = false) AprobacionPagoRequest request,
                                      Authentication auth) {
        return verificacionService.aprobar(id, request, auth.getName());
    }

    @PutMapping("/{id}/pago/rechazar")
    @PreAuthorize("hasAnyRole('Admin','Cajero')")
    @Operation(summary = "Rechazar el comprobante (Admin/Cajero)",
            description = "El pedido sigue pendiente con el stock reservado y el cliente puede mandar otro")
    public PedidoResponse rechazarPago(@PathVariable Long id,
                                       @Valid @RequestBody RechazoPagoRequest request,
                                       Authentication auth) {
        return verificacionService.rechazar(id, request, auth.getName());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Checkout", description = "Crea un pedido Pendiente con los items del carrito y compromete stock")
    public PedidoResponse checkout(
            @Valid @RequestBody CheckoutRequest request,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            Authentication auth) {
        return pedidoService.checkout(auth.getName(), request, idempotencyKey);
    }

    @GetMapping
    @Operation(summary = "Listar pedidos", description = "Admin y Cajero ven todas las ventas; los demás usuarios solo las propias")
    public PageResponse<PedidoResumenResponse> listar(
            Authentication auth,
            @ParameterObject @PageableDefault(size = 10, sort = "fecha", direction = Sort.Direction.DESC) Pageable pageable) {
        return pedidoService.listar(auth.getName(), esStaff(auth), pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalle de un pedido")
    public PedidoResponse obtener(@PathVariable Long id, Authentication auth) {
        return pedidoService.obtener(id, auth.getName(), esStaff(auth));
    }

    @GetMapping(value = "/{id}/factura/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Factura del pedido en PDF",
            description = "El dueño del pedido descarga su factura; Admin/Cajero pueden descargar cualquiera")
    public ResponseEntity<byte[]> facturaPdf(@PathVariable Long id, Authentication auth) {
        boolean esAdminOCajero = auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_Admin".equals(a.getAuthority()) || "ROLE_Cajero".equals(a.getAuthority()));
        PagoService.FacturaPdf pdf = pagoService.generarPdfPorPedido(id, auth.getName(), esAdminOCajero);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"factura-" + pdf.numero() + ".pdf\"")
                .body(pdf.contenido());
    }

    @PutMapping("/{id}/confirmar")
    @PreAuthorize("hasAnyRole('Admin','Cajero')")
    @Operation(summary = "Confirmar pedido (Admin/Cajero)", description = "Descuenta stock real y libera el comprometido")
    public PedidoResponse confirmar(@PathVariable Long id, Authentication auth) {
        return pedidoService.confirmar(id, auth.getName());
    }

    @PutMapping("/{id}/cancelar")
    @Operation(summary = "Anular pedido", description = "El dueño anula su pedido pendiente; Admin y Cajero pueden anular cualquiera")
    public PedidoResponse cancelar(@PathVariable Long id, Authentication auth) {
        return pedidoService.cancelar(id, auth.getName(), esStaff(auth));
    }

    @GetMapping("/{id}/comprobantes")
    @Operation(summary = "Comprobantes de pago del pedido",
            description = "Hasta " + PagoComprobanteService.MAXIMO_POR_PEDIDO + " por pedido")
    public List<PagoComprobanteResponse> comprobantes(@PathVariable Long id, Authentication auth) {
        return comprobanteService.listar(id, auth.getName(), esStaff(auth));
    }

    @PostMapping(value = "/{id}/comprobantes", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Adjuntar un comprobante de pago",
            description = "Imagen o PDF. El tope se valida en el servidor: la pantalla puede deshabilitar el boton, pero eso no impide mandar uno mas por la API")
    public PagoComprobanteResponse adjuntarComprobante(
            @PathVariable Long id,
            @RequestPart("archivo") MultipartFile archivo,
            Authentication auth) {
        return comprobanteService.adjuntar(id, archivo, auth.getName(), esStaff(auth));
    }

    @DeleteMapping("/{id}/comprobantes/{comprobanteId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Quitar un comprobante adjunto")
    public void quitarComprobante(
            @PathVariable Long id, @PathVariable Long comprobanteId, Authentication auth) {
        comprobanteService.eliminar(id, comprobanteId, auth.getName(), esStaff(auth));
    }

    @GetMapping("/{id}/comprobantes/{comprobanteId}/archivo")
    @Operation(summary = "Descargar un comprobante adjunto")
    public ResponseEntity<byte[]> archivoComprobante(
            @PathVariable Long id, @PathVariable Long comprobanteId, Authentication auth) {
        var archivo = comprobanteService.obtenerArchivo(id, comprobanteId, auth.getName(), esStaff(auth));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, archivo.contentType())
                .body(archivo.contenido());
    }

    private boolean esStaff(Authentication auth) {
        return auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_Admin".equals(a.getAuthority()) || "ROLE_Cajero".equals(a.getAuthority()));
    }
}
