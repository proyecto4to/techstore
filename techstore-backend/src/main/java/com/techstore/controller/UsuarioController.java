package com.techstore.controller;

import com.techstore.dto.request.CambioActivoRequest;
import com.techstore.dto.request.CrearUsuarioRequest;
import com.techstore.dto.request.ResetPasswordRequest;
import com.techstore.dto.response.PageResponse;
import com.techstore.dto.response.UsuarioAdminResponse;
import com.techstore.service.UsuarioService;
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
@RequestMapping("/api/v1/usuarios")
@RequiredArgsConstructor
@PreAuthorize("hasRole('Admin')")
@Tag(name = "Usuarios", description = "Gestión de usuarios del sistema (solo Admin)")
public class UsuarioController {

    private final UsuarioService usuarioService;

    @GetMapping
    @Operation(summary = "Listar usuarios")
    public PageResponse<UsuarioAdminResponse> listar(
            @ParameterObject @PageableDefault(size = 10, sort = "id", direction = Sort.Direction.ASC) Pageable pageable) {
        return usuarioService.listar(pageable);
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

    @PutMapping("/{id}/password")
    @Operation(summary = "Resetear la contraseña de un usuario")
    public UsuarioAdminResponse resetearPassword(
            @PathVariable Long id, @Valid @RequestBody ResetPasswordRequest request) {
        return usuarioService.resetearPassword(id, request.password());
    }
}
