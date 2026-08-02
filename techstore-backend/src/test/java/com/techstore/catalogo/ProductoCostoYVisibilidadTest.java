package com.techstore.catalogo;

import com.techstore.comun.Dinero;
import com.techstore.comun.Moneda;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Costo y visibilidad del producto")
class ProductoCostoYVisibilidadTest {

    private static Producto producto(int real, int comprometido) {
        Producto p = new Producto();
        p.setPrecio(Dinero.de(110_000, Moneda.PYG));
        p.setIva(BigDecimal.TEN);
        p.setStockReal(real);
        p.setStockComprometido(comprometido);
        p.setActivo(true);
        return p;
    }

    @Test
    @DisplayName("sin compras todavía no hay costo ni margen")
    void sinCostoNoHayMargen() {
        Producto p = producto(5, 0);

        // Nulo y no cero: un costo de cero diría que el producto es gratis y
        // daría un margen del 100%, que es peor que no saber.
        assertNull(p.getCosto());
        assertNull(p.getMargen());
    }

    @Test
    @DisplayName("el margen se mide sobre el precio sin IVA")
    void elMargenNoCuentaElImpuesto() {
        Producto p = producto(5, 0);
        p.registrarCosto(Dinero.de(60_000, Moneda.PYG), LocalDateTime.now());

        // 110.000 con IVA 10% son 100.000 sin IVA. El IVA no es ganancia: se
        // cobra para entregarlo. Margen = 100.000 − 60.000.
        assertEquals(Dinero.de(40_000, Moneda.PYG), p.getMargen());
    }

    @Test
    @DisplayName("el costo guarda cuándo se actualizó")
    void elCostoLlevaFecha() {
        Producto p = producto(5, 0);
        LocalDateTime cuando = LocalDateTime.of(2026, 8, 1, 10, 0);
        p.registrarCosto(Dinero.de(60_000, Moneda.PYG), cuando);

        // Un costo sin fecha no se puede interpretar: no es lo mismo uno de la
        // semana pasada que uno de hace dos años.
        assertEquals(cuando, p.getCostoActualizadoEn());
    }

    @Test
    @DisplayName("sin stock disponible desaparece de la tienda")
    void sinStockNoSeMuestra() {
        assertTrue(producto(5, 0).visibleEnTienda());
        assertFalse(producto(0, 0).visibleEnTienda());
        // Lo comprometido por pedidos pendientes ya está vendido: quedan 0
        // disponibles aunque el depósito tenga 2.
        assertFalse(producto(2, 2).visibleEnTienda());
    }

    @Test
    @DisplayName("se puede pedir que igual se muestre sin stock")
    void loQueSeFabricaAPedidoSiSeMuestra() {
        Producto p = producto(0, 0);
        p.setOcultarSinStock(false);

        assertTrue(p.visibleEnTienda());
    }

    @Test
    @DisplayName("un producto dado de baja no se muestra ni con stock")
    void elInactivoNuncaSeMuestra() {
        Producto p = producto(10, 0);
        p.setActivo(false);

        assertFalse(p.visibleEnTienda());
    }
}
