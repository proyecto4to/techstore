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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/pedidos")
@RequiredArgsConstructor
@Tag(name = "Pedidos", description = "Checkout y gestión de pedidos de venta")
public class PedidoController {

    private final PedidoService pedidoService;
    private final PagoService pagoService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Checkout", description = "Crea un pedido Pendiente con los items del carrito y compromete stock")
    public PedidoResponse checkout(@Valid @RequestBody CheckoutRequest request, Authentication auth) {
        return pedidoService.checkout(auth.getName(), request);
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
    public PedidoResponse confirmar(@PathVariable Long id) {
        return pedidoService.confirmar(id);
    }

    @PutMapping("/{id}/cancelar")
    @Operation(summary = "Anular pedido", description = "El dueño anula su pedido pendiente; Admin y Cajero pueden anular cualquiera")
    public PedidoResponse cancelar(@PathVariable Long id, Authentication auth) {
        return pedidoService.cancelar(id, auth.getName(), esStaff(auth));
    }

    private boolean esStaff(Authentication auth) {
        return auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_Admin".equals(a.getAuthority()) || "ROLE_Cajero".equals(a.getAuthority()));
    }
}
