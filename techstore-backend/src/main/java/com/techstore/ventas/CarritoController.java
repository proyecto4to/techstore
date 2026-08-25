package com.techstore.ventas;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/carrito")
@RequiredArgsConstructor
@Tag(name = "Carrito", description = "Revalidación pública del carrito contra el catálogo vigente")
public class CarritoController {

    private final CarritoService carritoService;

    @PostMapping("/validar")
    @SecurityRequirements
    @Operation(summary = "Revalidar precios y disponibilidad sin reservar stock")
    public ValidacionCarritoResponse validar(@Valid @RequestBody ValidarCarritoRequest request) {
        return carritoService.validar(request);
    }
}
