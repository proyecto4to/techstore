package com.techstore.dto.response;

import com.techstore.entity.Usuario;

public record UsuarioResponse(
        Long id,
        String email,
        String nombre,
        String apellido,
        String rol) {

    public static UsuarioResponse from(Usuario usuario) {
        return new UsuarioResponse(
                usuario.getId(),
                usuario.getEmail(),
                usuario.getNombre(),
                usuario.getApellido(),
                usuario.getRol().getNombre());
    }
}
