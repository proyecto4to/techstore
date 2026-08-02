package com.techstore.reportes;

import com.techstore.catalogo.Producto;
import com.techstore.catalogo.PuntoDeReposicion;

public record StockBajoResponse(
        Long productoId,
        String nombre,
        String marca,
        Integer stockReal,
        Integer stockComprometido,
        Integer stockDisponible,
        /** Stock a partir del cual conviene reponer. */
        Integer umbral,
        /**
         * {@code true} si el umbral salió del historial (cuánto se vende y
         * cuánto tarda el proveedor), {@code false} si es el mínimo cargado a
         * mano porque todavía no hay ventas registradas.
         */
        boolean umbralCalculado,
        /** Unidades por día según las ventas de los últimos meses. */
        Double demandaDiaria,
        /** Días que viene tardando el proveedor en traerlo. */
        Double diasEntrega,
        /**
         * Para cuántos días alcanza el stock actual. Es lo que hace entendible
         * el aviso: "quedan 3 días" dice más que "quedan 8 unidades".
         */
        Integer diasDeCobertura) {

    public static StockBajoResponse from(Producto producto, PuntoDeReposicion punto) {
        int disponible = producto.getStockDisponible();
        return new StockBajoResponse(
                producto.getId(),
                producto.getNombre(),
                producto.getMarca() == null ? null : producto.getMarca().getNombre(),
                producto.getStockReal(),
                producto.getStockComprometido(),
                disponible,
                punto.unidades(),
                punto.calculado(),
                punto.calculado() ? punto.demandaDiaria() : null,
                punto.calculado() ? punto.diasEntrega() : null,
                punto.diasDeCobertura(disponible));
    }
}
