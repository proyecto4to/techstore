package com.techstore.reportes;

import com.techstore.catalogo.Producto;

public record StockBajoResponse(
        Long productoId,
        String nombre,
        String marca,
        Integer stockReal,
        Integer stockComprometido,
        Integer stockDisponible,
        /** Punto a partir del cual se avisa: el propio del producto, o el general. */
        Integer umbral) {

    public static StockBajoResponse from(Producto producto) {
        return new StockBajoResponse(
                producto.getId(),
                producto.getNombre(),
                producto.getMarca() == null ? null : producto.getMarca().getNombre(),
                producto.getStockReal(),
                producto.getStockComprometido(),
                producto.getStockDisponible(),
                producto.umbralEfectivo());
    }
}
