package com.techstore.seguridad;

import java.util.List;

/**
 * La configuración completa de un rol, lista para dibujar.
 *
 * <p>Viaja con las etiquetas ya resueltas para que la pantalla no tenga que
 * conocer los nombres internos de los módulos ni traducirlos por su cuenta: si
 * mañana se agrega un módulo, aparece solo y con su nombre en castellano.
 */
public record MatrizPermisosResponse(
        String rol,
        List<FilaModulo> modulos,
        List<Pantalla> pantallas) {

    public record FilaModulo(String modulo, String etiqueta, List<Casilla> acciones) { }

    public record Casilla(String accion, String etiqueta, boolean permitido) { }

    public record Pantalla(String pantalla, boolean permitido) { }
}
