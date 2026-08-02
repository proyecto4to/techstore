package com.techstore.compras;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Lo que un proveedor ofrece de un producto, con la fecha del último precio. */
public record OfertaResponse(
        Long id,
        Long productoId,
        String productoNombre,
        String moneda,
        BigDecimal precio,
        boolean disponible,
        /** Un precio de hace dos años se compara distinto que uno de ayer. */
        LocalDateTime actualizadoEn) {

    public static OfertaResponse from(ProveedorProducto oferta) {
        return new OfertaResponse(
                oferta.getId(),
                oferta.getProducto().getId(),
                oferta.getProducto().getNombre(),
                oferta.getMoneda().codigo(),
                oferta.getPrecio().monto(),
                Boolean.TRUE.equals(oferta.getDisponible()),
                oferta.getActualizadoEn());
    }
}
