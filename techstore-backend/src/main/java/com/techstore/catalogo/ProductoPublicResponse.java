package com.techstore.catalogo;

import java.math.BigDecimal;

/**
 * Contrato de vidriera. Deliberadamente no contiene costo, margen, stock
 * físico/comprometido, mínimos, flags operativos ni timestamps internos.
 *
 * <p>La única cifra de existencias que sale es {@code maxPorPedido}, y sale
 * techada: no informa cuánto hay en depósito sino cuánto acepta el checkout,
 * que es lo que el comprador necesita para no elegir una cantidad que después
 * le van a rechazar. Con stock alto publica el tope fijo, así que el número
 * solo se vuelve informativo cuando queda poco.
 */
public record ProductoPublicResponse(
        Long id,
        String nombre,
        String descripcion,
        String moneda,
        BigDecimal precio,
        Integer iva,
        boolean disponible,
        /** Cuantas unidades admite el checkout ahora mismo; techado en 99. */
        Integer maxPorPedido,
        ProductoResponse.MarcaRef marca,
        ProductoResponse.ModeloRef modelo,
        String imagenUrl) {
}
