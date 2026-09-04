package com.techstore.seguridad;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Administración de roles, permisos y acceso a pantallas.
 *
 * <p>Solo Admin: quien puede repartir permisos puede dárselos a sí mismo, así
 * que el módulo que los administra tiene que ser el más cerrado de todos.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/seguridad")
@PreAuthorize("hasRole('Admin')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Seguridad", description = "Permisos por rol y acceso a pantallas")
public class SeguridadController {

    private final PermisoService service;

    @GetMapping("/roles")
    @Operation(summary = "Los roles que se pueden configurar")
    public List<String> roles() {
        return service.roles();
    }

    @GetMapping("/roles/{rol}/permisos")
    @Operation(summary = "Matriz de permisos y pantallas de un rol")
    public MatrizPermisosResponse matriz(@PathVariable String rol) {
        return service.matriz(rol);
    }

    @PutMapping("/roles/{rol}/permisos")
    @Operation(summary = "Cambia un permiso del rol",
            description = "Solo puede restringir: destildar cierra, tildar no abre lo que el backend ya niega")
    public MatrizPermisosResponse guardarPermiso(
            @PathVariable String rol,
            @Valid @RequestBody CambioPermisoRequest request) {
        service.guardarPermiso(rol, request.modulo(), request.accion(), request.permitido());
        return service.matriz(rol);
    }

    @PutMapping("/roles/{rol}/pantallas")
    @Operation(summary = "Habilita o deshabilita una pantalla para el rol")
    public MatrizPermisosResponse guardarPantalla(
            @PathVariable String rol,
            @Valid @RequestBody CambioPantallaRequest request) {
        service.guardarPantalla(rol, request.pantalla(), request.permitido());
        return service.matriz(rol);
    }

    public record CambioPermisoRequest(
            @NotNull Modulo modulo,
            @NotNull AccionPermiso accion,
            @NotNull Boolean permitido) { }

    public record CambioPantallaRequest(
            @NotBlank @Size(max = 60) String pantalla,
            @NotNull Boolean permitido) { }
}
