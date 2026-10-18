package com.techstore.promociones;

/**
 * A qué llega una promoción.
 *
 * <p>Es uno solo por promoción a propósito. Combinaciones del tipo «esta marca
 * menos estos dos productos» se arman con varias promociones, y cuál manda lo
 * resuelve {@link CalculadorDePrecios}: gana la de mayor descuento.
 */
public enum AlcancePromocion {
    /** Todo el catálogo. */
    TODOS("Todo el catálogo"),
    /** Un producto puntual. */
    PRODUCTO("Un producto"),
    /** Todos los productos de una marca. */
    MARCA("Una marca");

    private final String etiqueta;

    AlcancePromocion(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String etiqueta() {
        return etiqueta;
    }
}
