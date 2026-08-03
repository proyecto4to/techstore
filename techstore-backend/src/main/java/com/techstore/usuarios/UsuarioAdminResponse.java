package com.techstore.usuarios;

import com.techstore.empresa.PuntoExpedicion;

import java.time.LocalDateTime;

/**
 * @param puntoExpedicionId nulo = emite desde el punto por defecto de la empresa
 * @param puntoExpedicion   cómo mostrarlo, ya resuelto acá para que la pantalla
 *                          no tenga que cruzar la lista de puntos
 */
public record UsuarioAdminResponse(
        Long id,
        String email,
        String nombre,
        String apellido,
        String rol,
        Boolean activo,
        Long puntoExpedicionId,
        String puntoExpedicion,
        LocalDateTime lastLogin,
        LocalDateTime createdAt) {

    public static UsuarioAdminResponse from(Usuario usuario) {
        PuntoExpedicion punto = usuario.getPuntoExpedicion();
        return new UsuarioAdminResponse(
                usuario.getId(),
                usuario.getEmail(),
                usuario.getNombre(),
                usuario.getApellido(),
                usuario.getRol().getNombre(),
                usuario.getActivo(),
                punto == null ? null : punto.getId(),
                punto == null ? null : punto.prefijo() + " · " + punto.getNombre(),
                usuario.getLastLogin(),
                usuario.getCreatedAt());
    }
}
