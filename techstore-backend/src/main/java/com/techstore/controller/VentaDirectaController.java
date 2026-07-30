package com.techstore.controller;

import com.techstore.dto.request.VentaDirectaRequest;
import com.techstore.dto.response.PagoResponse;
import com.techstore.service.VentaDirectaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ventas")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('Admin','Cajero')")
@Tag(name = "Venta directa", description = "Ventas en mostrador, sin pedido previo por la web")
public class VentaDirectaController {

    private final VentaDirectaService ventaDirectaService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registrar una venta en mostrador",
            description = "Descuenta el stock, cobra y emite la factura en un solo paso. "
                    + "Si se indica un RUC, la factura sale a ese contribuyente.")
    public PagoResponse vender(@Valid @RequestBody VentaDirectaRequest request, Authentication auth) {
        return ventaDirectaService.vender(request, auth.getName());
    }
}
