package com.techstore.promociones;

import com.techstore.comun.Dinero;

/**
 * Lo que cuesta un producto ahora mismo, con la promoción ya resuelta.
 *
 * <p>{@code precio} es lo que se le va a cobrar a quien compre, y por eso es el
 * número que sale a la vidriera. {@code precioLista} es el precio cargado en el
 * producto: solo sirve para mostrar el «antes» tachado.
 */
public record PrecioVigente(
        Dinero precio,
        Dinero precioLista,
        Dinero descuento,
        PromocionAplicadaResponse promocion) {

    /** El caso normal: el producto vale lo que dice su precio de lista. */
    public static PrecioVigente sinPromocion(Dinero precioLista) {
        return new PrecioVigente(precioLista, precioLista, Dinero.cero(precioLista.moneda()), null);
    }

    public boolean tienePromocion() {
        return promocion != null;
    }
}
