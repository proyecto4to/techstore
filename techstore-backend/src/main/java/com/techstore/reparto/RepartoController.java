package com.techstore.reparto;

import com.techstore.comun.PageResponse;
import com.techstore.usuarios.UsuarioResponse;
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
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/repartos")
@RequiredArgsConstructor
@Tag(name = "Repartos", description = "Asignaciones, entregas, cobros y caja del repartidor")
public class RepartoController {
    private final RepartoService service;

    @PostMapping("/pedidos/{pedidoId}/asignar")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('Admin')")
    public RepartoResponse asignar(@PathVariable Long pedidoId,
                                   @Valid @RequestBody AsignarRepartoRequest request,
                                   Authentication auth) {
        return service.asignar(pedidoId, request, auth.getName());
    }

    @GetMapping("/pedidos/{pedidoId}/asignaciones")
    @PreAuthorize("hasRole('Admin')")
    public List<RepartoResponse> asignacionesPedido(@PathVariable Long pedidoId) {
        return service.asignacionesPedido(pedidoId);
    }

    @GetMapping("/mios")
    @PreAuthorize("hasRole('Repartidor')")
    public PageResponse<RepartoResponse> mios(
            @RequestParam(required = false) Set<EstadoReparto> estado,
            @ParameterObject @PageableDefault(size = 30, sort = "ordenEntrega",
                    direction = Sort.Direction.ASC) Pageable pageable,
            Authentication auth) {
        return service.listarPropias(auth.getName(), estado, pageable);
    }

    @GetMapping("/clientes")
    @PreAuthorize("hasRole('Repartidor')")
    public List<EntregasClienteResponse> clientes(Authentication auth) {
        return service.agruparPorCliente(auth.getName());
    }

    @GetMapping("/ruta")
    @PreAuthorize("hasRole('Repartidor')")
    public RutaOptimizadaResponse ruta(@RequestParam(required = false) BigDecimal latitud,
                                       @RequestParam(required = false) BigDecimal longitud,
                                       Authentication auth) {
        return service.ruta(auth.getName(), latitud, longitud);
    }

    @GetMapping("/{id}/historial")
    @PreAuthorize("hasAnyRole('Admin','Repartidor')")
    public List<HistorialRepartoResponse> historial(@PathVariable Long id, Authentication auth) {
        return service.historial(id, auth.getName(), tieneRol(auth, "ROLE_Admin"));
    }

    @PutMapping("/{id}/estado")
    @PreAuthorize("hasRole('Repartidor')")
    public RepartoResponse cambiarEstado(@PathVariable Long id,
                                         @Valid @RequestBody CambiarEstadoRepartoRequest request,
                                         Authentication auth) {
        return service.cambiarEstado(id, request, auth.getName());
    }

    @PostMapping("/{id}/cobro")
    @PreAuthorize("hasRole('Repartidor')")
    @Operation(summary = "Registrar una sola vez el cobro en efectivo de la entrega")
    public RepartoResponse cobrar(@PathVariable Long id, Authentication auth) {
        return service.registrarCobro(id, auth.getName());
    }

    @GetMapping("/dashboard")
    @PreAuthorize("hasRole('Repartidor')")
    public DashboardRepartidorResponse dashboard(Authentication auth) {
        return service.dashboard(auth.getName());
    }

    @GetMapping("/caja/saldo")
    @PreAuthorize("hasRole('Repartidor')")
    public java.util.Map<String, Object> saldo(Authentication auth) {
        return java.util.Map.of("saldo", service.saldoActual(auth.getName()), "moneda", "PYG");
    }

    @GetMapping("/caja/movimientos")
    @PreAuthorize("hasRole('Repartidor')")
    public PageResponse<MovimientoCajaRepartidorResponse> movimientos(
            @ParameterObject @PageableDefault(size = 30) Pageable pageable, Authentication auth) {
        return service.movimientos(auth.getName(), pageable);
    }

    @GetMapping("/caja/cajeros")
    @PreAuthorize("hasRole('Repartidor')")
    public List<UsuarioResponse> cajeros() { return service.cajeros(); }

    @PostMapping("/caja/vaciar")
    @PreAuthorize("hasRole('Repartidor')")
    public TransferenciaResponse vaciar(@Valid @RequestBody SolicitarTransferenciaRequest request,
                                        Authentication auth) {
        return service.solicitarVaciado(auth.getName(), request);
    }

    @GetMapping("/caja/transferencias")
    @PreAuthorize("hasRole('Repartidor')")
    public PageResponse<TransferenciaResponse> transferencias(
            @ParameterObject @PageableDefault(size = 30) Pageable pageable, Authentication auth) {
        return service.transferenciasPropias(auth.getName(), pageable);
    }

    @GetMapping("/solicitudes")
    @PreAuthorize("hasRole('Cajero')")
    public PageResponse<TransferenciaResponse> solicitudes(
            @ParameterObject @PageableDefault(size = 30) Pageable pageable, Authentication auth) {
        return service.solicitudesCajero(auth.getName(), pageable);
    }

    @PutMapping("/solicitudes/{id}/aceptar")
    @PreAuthorize("hasRole('Cajero')")
    public TransferenciaResponse aceptar(@PathVariable Long id, Authentication auth) {
        return service.aceptarTransferencia(id, auth.getName());
    }

    @PutMapping("/solicitudes/{id}/rechazar")
    @PreAuthorize("hasRole('Cajero')")
    public TransferenciaResponse rechazar(@PathVariable Long id,
                                          @Valid @RequestBody RechazarTransferenciaRequest request,
                                          Authentication auth) {
        return service.rechazarTransferencia(id, request, auth.getName());
    }

    private boolean tieneRol(Authentication auth, String rol) {
        return auth.getAuthorities().stream().anyMatch(a -> rol.equals(a.getAuthority()));
    }
}
