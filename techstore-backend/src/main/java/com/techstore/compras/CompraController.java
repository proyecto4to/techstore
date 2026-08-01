package com.techstore.compras;

import com.techstore.comun.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
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
@RequestMapping("/api/v1/compras")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('Admin','Cajero')")
@Tag(name = "Compras", description = "Pedidos de compra a proveedores y recepción de stock")
public class CompraController {

    private final CompraService compraService;

    @GetMapping
    @Operation(summary = "Listar compras")
    public PageResponse<CompraResumenResponse> listar(
            @ParameterObject @PageableDefault(size = 10, sort = "fecha", direction = Sort.Direction.DESC) Pageable pageable) {
        return compraService.listar(pageable);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registrar pedido de compra",
            description = "Queda Pendiente hasta que se reciba la mercadería; no afecta el stock todavía")
    public CompraResumenResponse crear(@Valid @RequestBody CompraRequest request, Authentication auth) {
        return compraService.crear(request, auth.getName());
    }

    @PutMapping("/{id}/recibir")
    @Operation(summary = "Recibir la compra", description = "Suma la cantidad de cada item al stock real")
    public CompraResumenResponse recibir(@PathVariable Long id) {
        return compraService.recibir(id);
    }

    @PutMapping("/{id}/pagar")
    @Operation(summary = "Pagar la compra al proveedor",
            description = "Registra el pago como egreso en caja. Puede repartirse entre efectivo y "
                    + "transferencia; la suma debe igualar el total de la compra")
    public CompraResumenResponse pagar(
            @PathVariable Long id, @Valid @RequestBody PagoCompraRequest request, Authentication auth) {
        return compraService.pagar(id, request.lineas(), auth.getName());
    }

    @PutMapping("/{id}/anular")
    @Operation(summary = "Anular una compra pendiente")
    public CompraResumenResponse anular(@PathVariable Long id) {
        return compraService.anular(id);
    }
}
