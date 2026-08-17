package com.techstore.envios;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/envios")
@RequiredArgsConstructor
@Tag(name = "Envíos", description = "Cotización, tarifas configurables y seguimiento")
public class EnvioController {
    private final EnvioService service;

    @PostMapping("/cotizar")
    @Operation(summary = "Cotizar métodos de entrega con reglas del backend")
    public List<CotizacionEnvioResponse> cotizar(@Valid @RequestBody CotizarEnvioRequest request, Authentication auth) {
        return service.cotizar(auth.getName(), request);
    }

    @GetMapping("/pedidos/{pedidoId}")
    @Operation(summary = "Consultar tracking del envío de un pedido")
    public EnvioResponse tracking(@PathVariable Long pedidoId, Authentication auth) {
        return service.tracking(pedidoId, auth.getName(), esStaff(auth));
    }

    @PutMapping("/pedidos/{pedidoId}/estado")
    @PreAuthorize("hasAnyRole('Admin','Cajero')")
    @Operation(summary = "Registrar un estado y evento de seguimiento")
    public EnvioResponse actualizar(@PathVariable Long pedidoId, @Valid @RequestBody ActualizarEnvioRequest request) {
        return service.actualizar(pedidoId, request);
    }

    @GetMapping("/tarifas")
    @PreAuthorize("hasRole('Admin')")
    public List<TarifaEnvioResponse> listarTarifas() { return service.listarTarifas(); }

    @PostMapping("/tarifas")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('Admin')")
    public TarifaEnvioResponse crearTarifa(@Valid @RequestBody TarifaEnvioRequest request) {
        return service.crearTarifa(request);
    }

    @PutMapping("/tarifas/{id}")
    @PreAuthorize("hasRole('Admin')")
    public TarifaEnvioResponse actualizarTarifa(@PathVariable Long id, @Valid @RequestBody TarifaEnvioRequest request) {
        return service.actualizarTarifa(id, request);
    }

    private boolean esStaff(Authentication auth) {
        return auth.getAuthorities().stream().anyMatch(a ->
                "ROLE_Admin".equals(a.getAuthority()) || "ROLE_Cajero".equals(a.getAuthority()));
    }
}
