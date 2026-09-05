package com.techstore.mensajeria;

import com.techstore.usuarios.Usuario;

/** Alguien a quien se le puede escribir, tal como se lista al elegir. */
public record DestinatarioResponse(
        Long id,
        String nombreCompleto,
        String email,
        String rol,
        /** Id de la conversación que ya existe con esa persona, si la hay. */
        Long conversacionId) {

    public static DestinatarioResponse from(Usuario usuario, Long conversacionId) {
        return new DestinatarioResponse(
                usuario.getId(),
                usuario.getNombre() + " " + usuario.getApellido(),
                usuario.getEmail(),
                usuario.getRol().getNombre(),
                conversacionId);
    }
}
