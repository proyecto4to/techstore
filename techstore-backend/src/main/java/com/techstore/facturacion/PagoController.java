package com.techstore.facturacion;

import com.techstore.comun.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Pagos y facturas", description = "Cobro de pedidos confirmados y emisión de facturas")
public class PagoController {

    private final PagoService pagoService;

    @PostMapping("/pedidos/{pedidoId}/pago")
    @PreAuthorize("hasAnyRole('Admin','Cajero')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registrar pago (Admin/Cajero)",
            description = "Cobra el total de un pedido Confirmado y emite la factura en la misma transacción")
    public PagoResponse registrarPago(
            @PathVariable Long pedidoId,
            @Valid @RequestBody RegistroPagoRequest request,
            Authentication auth) {
        return pagoService.registrarPago(pedidoId, request, auth.getName());
    }

    @GetMapping("/facturas")
    @Operation(summary = "Listar facturas",
            description = "Admin/Cajero ven todas; los clientes solo las facturas de sus pedidos")
    public PageResponse<FacturaResponse> listarFacturas(
            Authentication auth,
            @ParameterObject @PageableDefault(size = 10, sort = "fecha", direction = Sort.Direction.DESC) Pageable pageable) {
        return pagoService.listarFacturas(auth.getName(), esStaff(auth), pageable);
    }

    @GetMapping("/facturas/{id}")
    @Operation(summary = "Detalle de una factura",
            description = "El cliente puede consultar solo una factura propia")
    public FacturaResponse obtenerFactura(@PathVariable Long id, Authentication auth) {
        return pagoService.obtenerFactura(id, auth.getName(), esStaff(auth));
    }

    @GetMapping(value = "/facturas/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Factura en PDF",
            description = "El cliente puede descargar solo una factura propia; Admin/Cajero pueden descargar cualquiera")
    public ResponseEntity<byte[]> facturaPdf(@PathVariable Long id, Authentication auth) {
        PagoService.FacturaPdf pdf = pagoService.generarPdf(id, auth.getName(), esStaff(auth));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"factura-" + pdf.numero() + ".pdf\"")
                .body(pdf.contenido());
    }

    private boolean esStaff(Authentication auth) {
        return auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_Admin".equals(a.getAuthority()) || "ROLE_Cajero".equals(a.getAuthority()));
    }
}
