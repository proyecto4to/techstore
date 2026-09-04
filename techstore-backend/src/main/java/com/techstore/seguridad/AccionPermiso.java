package com.techstore.seguridad;

import org.springframework.http.HttpMethod;

/**
 * Qué se puede hacer sobre un módulo.
 *
 * <p>Se mapean desde el método HTTP porque es lo que de verdad distingue una
 * consulta de un alta: no hace falta que cada endpoint declare su acción, y un
 * endpoint nuevo queda cubierto por el mismo criterio sin tocar nada.
 */
public enum AccionPermiso {
    VER("Ver"),
    CREAR("Crear"),
    EDITAR("Editar"),
    ELIMINAR("Eliminar");

    private final String etiqueta;

    AccionPermiso(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String etiqueta() {
        return etiqueta;
    }

    /** La acción que representa un método HTTP. */
    public static AccionPermiso deMetodo(String metodo) {
        if (HttpMethod.POST.matches(metodo)) {
            return CREAR;
        }
        if (HttpMethod.PUT.matches(metodo) || HttpMethod.PATCH.matches(metodo)) {
            return EDITAR;
        }
        if (HttpMethod.DELETE.matches(metodo)) {
            return ELIMINAR;
        }
        return VER;
    }
}
