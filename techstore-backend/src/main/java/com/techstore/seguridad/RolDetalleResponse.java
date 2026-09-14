package com.techstore.seguridad;

import com.techstore.usuarios.Rol;

/**
 * Un rol como lo ve quien administra: para qué es, si se puede asignar y a
 * cuánta gente afecta tocarlo.
 *
 * @param usuarios      cuántas personas lo tienen hoy; cambiarle los permisos
 *                      les cambia el trabajo a todas ellas
 * @param administrador si es el rol que gobierna la Seguridad. El sistema no lo
 *                      deja desactivar ni dejar sin gente: quedarse sin
 *                      administradores no tiene vuelta atrás desde la interfaz.
 */
public record RolDetalleResponse(
        Long id,
        String nombre,
        String descripcion,
        Boolean activo,
        long usuarios,
        boolean administrador) {

    public static RolDetalleResponse from(Rol rol, long usuarios) {
        return new RolDetalleResponse(
                rol.getId(),
                rol.getNombre(),
                rol.getDescripcion(),
                rol.getActivo(),
                usuarios,
                RolesDelSistema.esAdministrador(rol.getNombre()));
    }
}
