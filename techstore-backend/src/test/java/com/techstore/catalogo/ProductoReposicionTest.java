package com.techstore.catalogo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * La regla de reposición: qué se mide, contra qué, y qué pasa cuando el
 * producto no tiene un mínimo propio.
 */
class ProductoReposicionTest {

    private static Producto producto(int real, int comprometido, int minimo) {
        Producto p = new Producto();
        p.setStockReal(real);
        p.setStockComprometido(comprometido);
        p.setStockMinimo(minimo);
        return p;
    }

    @Test
    void avisaCuandoElDisponibleLlegaAlMinimoPropio() {
        assertTrue(producto(3, 0, 3).necesitaReposicion());
        assertTrue(producto(2, 0, 3).necesitaReposicion());
        assertFalse(producto(4, 0, 3).necesitaReposicion());
    }

    @Test
    void mideContraElDisponibleYNoContraElReal() {
        // 10 en el depósito pero 8 ya vendidos en pedidos pendientes: quedan 2
        // realmente disponibles, así que hay que reponer aunque el real sea alto.
        assertTrue(producto(10, 8, 3).necesitaReposicion());
        assertEquals(2, producto(10, 8, 3).getStockDisponible());
    }

    @Test
    void sinMinimoPropioUsaElUmbralGeneral() {
        assertEquals(Producto.UMBRAL_POR_DEFECTO, producto(100, 0, 0).umbralEfectivo());
        assertTrue(producto(Producto.UMBRAL_POR_DEFECTO, 0, 0).necesitaReposicion());
        assertFalse(producto(Producto.UMBRAL_POR_DEFECTO + 1, 0, 0).necesitaReposicion());
    }

    @Test
    void elMinimoPropioLePasaPorEncimaAlGeneral() {
        // Con mínimo 1, tener 3 disponibles no dispara el aviso aunque el
        // umbral general (5) sí lo haría.
        assertEquals(1, producto(3, 0, 1).umbralEfectivo());
        assertFalse(producto(3, 0, 1).necesitaReposicion());
    }

    @Test
    void toleraLosNulosDeUnProductoReciénCreado() {
        Producto vacio = new Producto();
        vacio.setStockReal(null);
        vacio.setStockComprometido(null);
        vacio.setStockMinimo(null);
        assertEquals(0, vacio.getStockDisponible());
        assertEquals(Producto.UMBRAL_POR_DEFECTO, vacio.umbralEfectivo());
        assertTrue(vacio.necesitaReposicion());
    }
}
