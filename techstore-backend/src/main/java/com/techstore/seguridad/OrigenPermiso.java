package com.techstore.seguridad;

/**
 * De dónde sale el permiso que terminó aplicándose.
 *
 * <p>Sin esto, la pantalla de permisos efectivos muestra tildes sin explicar
 * nada: quien administra necesita saber si algo está prendido porque lo trae el
 * rol o porque alguien se lo dio a esa persona, que son dos situaciones muy
 * distintas a la hora de sacárselo.
 */
public enum OrigenPermiso {

    /** Lo trae la matriz del rol. */
    ROL("Heredado del rol"),

    /** Se le dio a esta persona en particular. */
    CONCEDIDO("Asignado individualmente"),

    /** Se le quitó a esta persona en particular, aunque el rol lo permita. */
    RESTRINGIDO("Restringido individualmente"),

    /**
     * Nadie lo configuró nunca.
     *
     * <p>Queda permitido porque así se comportaba el sistema antes de que
     * existiera la matriz, y activar la capa no podía dejar a nadie afuera. Se
     * muestra aparte para que no se lea como una decisión que alguien tomó.
     */
    PREDETERMINADO("Permitido por omisión");

    private final String etiqueta;

    OrigenPermiso(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String etiqueta() {
        return etiqueta;
    }
}
