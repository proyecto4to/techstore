package com.techstore.seguridad;

/**
 * El único lugar donde el código nombra a un rol.
 *
 * <p>Los roles son datos y se crean desde la pantalla de Seguridad; ninguna
 * regla de negocio pregunta por su nombre. La excepción es el administrador,
 * porque alguien tiene que poder repartir los permisos y ese alguien no puede
 * depender de una fila que se pueda borrar. Está acá, con nombre, en vez de
 * repetido en cada servicio: si algún día se renombra, se renombra una vez.
 *
 * <p>El nombre coincide con el de las anotaciones {@code hasRole('Admin')} que
 * ya protegen los controladores. Cambiarlo acá sin cambiarlas a ellas dejaría
 * las dos capas discutiendo, así que van juntas.
 */
public final class RolesDelSistema {

    /** Quien administra la Seguridad. */
    public static final String ADMINISTRADOR = "Admin";

    private RolesDelSistema() {
    }

    public static boolean esAdministrador(String rol) {
        return ADMINISTRADOR.equalsIgnoreCase(rol);
    }
}
