package com.techstore.promociones;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * La promoción que explica un precio, tal como la ve quien compra.
 *
 * <p>Va adosada al producto en el catálogo y en el carrito. No lleva el alcance
 * ni las fechas de inicio: a quien compra le importa cuánto descuenta y hasta
 * cuándo le dura.
 */
public record PromocionAplicadaResponse(
        Long id,
        String nombre,
        BigDecimal porcentaje,
        LocalDateTime hasta) {

    public static PromocionAplicadaResponse de(Promocion promocion) {
        return new PromocionAplicadaResponse(
                promocion.getId(),
                promocion.getNombre(),
                promocion.getPorcentaje(),
                promocion.getHasta());
    }
}
