package com.techstore.usuarios;

public record UsuarioResponse(
        Long id,
        String email,
        String nombre,
        String apellido,
        String rol,
        /** Datos fiscales para facturar como contribuyente; null = consumidor final. */
        String ruc,
        String razonSocial) {

    public static UsuarioResponse from(Usuario usuario) {
        return new UsuarioResponse(
                usuario.getId(),
                usuario.getEmail(),
                usuario.getNombre(),
                usuario.getApellido(),
                usuario.getRol().getNombre(),
                usuario.getRuc(),
                usuario.getRazonSocial());
    }
}
