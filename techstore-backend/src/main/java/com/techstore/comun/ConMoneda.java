package com.techstore.comun;

import java.math.BigDecimal;

/**
 * Lo implementa toda entidad que guarda importes. Aporta las dos conversiones
 * entre la columna {@code NUMERIC} y el tipo {@link Dinero}, para que cada
 * entidad no las repita.
 *
 * <p>La moneda es del documento entero, no de cada importe: una factura no
 * puede tener el subtotal en guaraníes y el total en dólares. De ahí que
 * {@link #monto(Dinero)} rechace un importe en otra moneda en vez de guardarlo
 * como si nada.
 */
public interface ConMoneda {

    /** La moneda del documento. La genera Lombok sobre el campo {@code moneda}. */
    Moneda getMoneda();

    /** Columna {@code NUMERIC} a importe, en la moneda del documento. */
    default Dinero importe(BigDecimal monto) {
        return Dinero.deONulo(monto, getMoneda());
    }

    /** Importe a columna {@code NUMERIC}, exigiendo que sea de la moneda del documento. */
    default BigDecimal monto(Dinero importe) {
        if (importe == null) {
            return null;
        }
        if (importe.moneda() != getMoneda()) {
            throw new Dinero.MonedasIncompatiblesException(getMoneda(), importe.moneda());
        }
        return importe.monto();
    }
}
