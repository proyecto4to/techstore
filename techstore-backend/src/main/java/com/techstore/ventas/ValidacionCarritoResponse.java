package com.techstore.ventas;

import com.techstore.catalogo.ProductoPublicResponse;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record ValidacionCarritoResponse(
        boolean valido,
        List<Linea> items,
        Map<String, BigDecimal> totales) {

    public record Linea(
            Long productoId,
            Integer cantidad,
            EstadoLineaCarrito estado,
            ProductoPublicResponse producto,
            BigDecimal subtotal) {
    }
}
