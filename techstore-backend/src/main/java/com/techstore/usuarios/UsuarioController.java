package com.techstore.usuarios;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/usuarios")
@RequiredArgsConstructor
@PreAuthorize("hasRole('Admin')")
@Tag(name = "Usuarios", description = "Gestión de usuarios del sistema (solo Admin)")
public class UsuarioController {

    private final UsuarioService usuarioService;

    @GetMapping
    @Operation(summary = "Listar usuarios",
            description = "Filtros opcionales: texto libre sobre nombre, apellido y correo; "
                    + "rol exacto; y si está activo. Se aplican en la base, no sobre la página")
    public PageResponse<UsuarioAdminResponse> listar(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String rol,
            @RequestParam(required = false) Boolean activo,
            @ParameterObject @PageableDefault(size = 10, sort = "id", direction = Sort.Direction.ASC) Pageable pageable) {
        return usuarioService.buscar(q, rol, activo, pageable);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crear usuario con rol (Admin, Cajero o Cliente)")
    public UsuarioAdminResponse crear(@Valid @RequestBody CrearUsuarioRequest request) {
        return usuarioService.crear(request);
    }

    @PutMapping("/{id}/activo")
    @Operation(summary = "Activar o desactivar un usuario",
            description = "Un usuario desactivado no puede iniciar sesión. No podés desactivarte a vos mismo.")
    public UsuarioAdminResponse cambiarActivo(
            @PathVariable Long id, @Valid @RequestBody CambioActivoRequest request, Authentication auth) {
        return usuarioService.cambiarActivo(id, request.activo(), auth.getName());
    }

    @PutMapping("/{id}/rol")
    @Operation(summary = "Cambiar el rol de un usuario",
            description = "Cambia de golpe todo lo que puede hacer, así que queda auditado. "
                    + "No podés bajarte a vos mismo de administrador ni dejar al sistema sin ninguno")
    public UsuarioAdminResponse asignarRol(
            @PathVariable Long id, @Valid @RequestBody AsignarRolRequest request, Authentication auth) {
        return usuarioService.asignarRol(id, request.rol(), auth.getName());
    }

    @PutMapping("/{id}/punto-expedicion")
    @Operation(summary = "Asignar desde qué punto de expedición emite el usuario",
            description = "Decide el prefijo y la serie de numeración de las facturas que emita. "
                    + "Mandar el id en nulo lo devuelve al punto por defecto de la empresa.")
    public UsuarioAdminResponse asignarPuntoExpedicion(
            @PathVariable Long id, @Valid @RequestBody AsignarPuntoExpedicionRequest request) {
        return usuarioService.asignarPuntoExpedicion(id, request.puntoExpedicionId());
    }

    @PutMapping("/{id}/password")
    @Operation(summary = "Resetear la contraseña de un usuario")
    public UsuarioAdminResponse resetearPassword(
            @PathVariable Long id, @Valid @RequestBody ResetPasswordRequest request) {
        return usuarioService.resetearPassword(id, request.password());
    }
}
