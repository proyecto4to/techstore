package com.techstore.vehiculos;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/vehiculos")
@RequiredArgsConstructor
public class VehiculoController {
    private final VehiculoService service;

    @GetMapping
    @PreAuthorize("hasAnyRole('Admin','Repartidor')")
    public List<VehiculoResponse> listar(Authentication auth) {
        return service.listar(tieneRol(auth, "ROLE_Admin"));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('Admin')")
    public VehiculoResponse crear(@Valid @RequestBody VehiculoRequest request) {
        return service.crear(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('Admin')")
    public VehiculoResponse actualizar(@PathVariable Long id, @Valid @RequestBody VehiculoRequest request) {
        return service.actualizar(id, request);
    }

    @PutMapping("/{id}/activo")
    @PreAuthorize("hasRole('Admin')")
    public VehiculoResponse activo(@PathVariable Long id,
                                   @Valid @RequestBody CambioActivoVehiculoRequest request) {
        return service.cambiarActivo(id, request.activo());
    }

    @GetMapping("/{id}/historial")
    @PreAuthorize("hasRole('Admin')")
    public HistorialVehiculoResponse historial(@PathVariable Long id) {
        return service.historial(id);
    }

    @PutMapping("/eventos/{id}/resolver")
    @PreAuthorize("hasRole('Admin')")
    public EventoVehiculoResponse resolver(@PathVariable Long id,
                                            @Valid @RequestBody ResolverEventoVehiculoRequest request) {
        return service.resolverEvento(id, request);
    }

    @PostMapping("/{id}/usar")
    @PreAuthorize("hasRole('Repartidor')")
    public UsoVehiculoResponse usar(@PathVariable Long id,
                                    @Valid @RequestBody IniciarUsoVehiculoRequest request,
                                    Authentication auth) {
        return service.iniciarUso(id, request, auth.getName());
    }

    @GetMapping("/mi-uso")
    @PreAuthorize("hasRole('Repartidor')")
    public UsoVehiculoResponse miUso(Authentication auth) {
        return service.usoActual(auth.getName());
    }

    @PostMapping("/mi-uso/eventos")
    @PreAuthorize("hasRole('Repartidor')")
    public EventoVehiculoResponse evento(@Valid @RequestBody EventoVehiculoRequest request,
                                          Authentication auth) {
        return service.registrarEvento(request, auth.getName());
    }

    @PostMapping("/mi-uso/finalizar")
    @PreAuthorize("hasRole('Repartidor')")
    public UsoVehiculoResponse finalizar(@Valid @RequestBody FinalizarUsoVehiculoRequest request,
                                          Authentication auth) {
        return service.finalizarUso(request, auth.getName());
    }

    private boolean tieneRol(Authentication auth, String rol) {
        return auth.getAuthorities().stream().anyMatch(a -> rol.equals(a.getAuthority()));
    }
}
