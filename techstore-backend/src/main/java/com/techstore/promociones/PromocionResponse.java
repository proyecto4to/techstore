package com.techstore.promociones;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Una promoción como la ve administración.
 *
 * <p>Trae {@code vigente} ya resuelto para que la pantalla no tenga que
 * comparar fechas por su cuenta: activa y dentro de la ventana no es lo mismo,
 * y esa distinción es justamente la que confunde al mirar una lista.
 */
public record PromocionResponse(
        Long id,
        String nombre,
        BigDecimal porcentaje,
        AlcancePromocion alcance,
        String alcanceEtiqueta,
        Long productoId,
        String productoNombre,
        Long marcaId,
        String marcaNombre,
        LocalDateTime desde,
        LocalDateTime hasta,
        boolean activa,
        boolean vigente) {

    public static PromocionResponse de(Promocion promocion, LocalDateTime momento) {
        return new PromocionResponse(
                promocion.getId(),
                promocion.getNombre(),
                promocion.getPorcentaje(),
                promocion.getAlcance(),
                promocion.getAlcance().etiqueta(),
                promocion.getProducto() == null ? null : promocion.getProducto().getId(),
                promocion.getProducto() == null ? null : promocion.getProducto().getNombre(),
                promocion.getMarca() == null ? null : promocion.getMarca().getId(),
                promocion.getMarca() == null ? null : promocion.getMarca().getNombre(),
                promocion.getDesde(),
                promocion.getHasta(),
                Boolean.TRUE.equals(promocion.getActiva()),
                promocion.vigenteEn(momento));
    }
}
