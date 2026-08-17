package com.techstore.comun;

import java.math.BigDecimal;

/**
 * Una línea de un documento que lleva IVA: un importe y la tasa con la que se
 * calcula el impuesto que ya trae adentro.
 *
 * <p>Existe para que el desglose por tasa se escriba una sola vez. Las líneas
 * de un pedido y las de una nota de crédito son cosas distintas, pero para
 * sumar gravadas al 10%, gravadas al 5% y exentas dan exactamente lo mismo, y
 * antes cada lugar que lo necesitaba lo recorría por su cuenta.
 *
 * @see DesgloseIva
 */
public interface LineaGravada {

    /** Importe de la línea, con el IVA incluido. */
    Dinero getSubtotal();

    /** Tasa en porcentaje: 10, 5, o 0/nulo si está exenta. */
    BigDecimal getIvaAplicado();
}
