package com.techstore.catalogo;

import com.techstore.promociones.PrecioVigente;
import com.techstore.promociones.PromocionAplicadaResponse;

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
        /**
         * Lo que se le va a cobrar a quien compre: si hay una promoción
         * vigente, ya viene descontado.
         *
         * <p>Que el descuento entre acá y no solo en un campo nuevo es
         * deliberado. Una app vieja, que no sabe que existen las promociones,
         * muestra este número y muestra el correcto; si la rebaja viviera
         * únicamente en {@code promocion}, esa app publicaría un precio más
         * alto que el que después cobra el checkout.
         */
        BigDecimal precio,
        Integer iva,
        boolean disponible,
        /** Cuantas unidades admite el checkout ahora mismo; techado en 99. */
        Integer maxPorPedido,
        ProductoResponse.MarcaRef marca,
        ProductoResponse.ModeloRef modelo,
        String imagenUrl,
        /** El precio de lista, solo cuando hay promoción: es el «antes». */
        BigDecimal precioLista,
        /** Qué promoción explica la rebaja; nulo si no hay ninguna. */
        PromocionAplicadaResponse promocion) {

    /** El mismo producto con la promoción vigente ya aplicada. */
    public ProductoPublicResponse con(PrecioVigente precioVigente) {
        if (!precioVigente.tienePromocion()) {
            return this;
        }
        return new ProductoPublicResponse(
                id, nombre, descripcion, moneda,
                precioVigente.precio().monto(),
                iva, disponible, maxPorPedido, marca, modelo, imagenUrl,
                precioVigente.precioLista().monto(),
                precioVigente.promocion());
    }
}
