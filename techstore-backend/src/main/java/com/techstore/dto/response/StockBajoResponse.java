package com.techstore.dto.response;

import com.techstore.entity.Producto;

public record StockBajoResponse(
        Long productoId,
        String nombre,
        String marca,
        Integer stockReal,
        Integer stockComprometido,
        Integer stockDisponible) {

    public static StockBajoResponse from(Producto producto) {
        return new StockBajoResponse(
                producto.getId(),
                producto.getNombre(),
                producto.getMarca() == null ? null : producto.getMarca().getNombre(),
                producto.getStockReal(),
                producto.getStockComprometido(),
                producto.getStockDisponible());
    }
}
