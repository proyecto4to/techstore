package com.techstore.dto.response;

import com.techstore.entity.Usuario;

import java.time.LocalDateTime;

public record UsuarioAdminResponse(
        Long id,
        String email,
        String nombre,
        String apellido,
        String rol,
        Boolean activo,
        LocalDateTime lastLogin,
        LocalDateTime createdAt) {

    public static UsuarioAdminResponse from(Usuario usuario) {
        return new UsuarioAdminResponse(
                usuario.getId(),
                usuario.getEmail(),
                usuario.getNombre(),
                usuario.getApellido(),
                usuario.getRol().getNombre(),
                usuario.getActivo(),
                usuario.getLastLogin(),
                usuario.getCreatedAt());
    }
}
