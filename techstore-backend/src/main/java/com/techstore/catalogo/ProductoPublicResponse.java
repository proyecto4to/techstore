package com.techstore.catalogo;

import java.math.BigDecimal;

/**
 * Contrato de vidriera. Deliberadamente no contiene costo, margen, stock
 * físico/comprometido, mínimos, flags operativos ni timestamps internos.
 */
public record ProductoPublicResponse(
        Long id,
        String nombre,
        String descripcion,
        String moneda,
        BigDecimal precio,
        Integer iva,
        boolean disponible,
        ProductoResponse.MarcaRef marca,
        ProductoResponse.ModeloRef modelo,
        String imagenUrl) {
}
