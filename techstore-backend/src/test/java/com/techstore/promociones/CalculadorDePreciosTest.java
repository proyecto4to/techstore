package com.techstore.promociones;

import com.techstore.catalogo.Marca;
import com.techstore.catalogo.Producto;
import com.techstore.comun.Dinero;
import com.techstore.comun.Moneda;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Las reglas de precio con promociones, que son las que después explican por
 * qué un pedido salió lo que salió.
 */
class CalculadorDePreciosTest {

    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 9, 22, 10, 0);

    @Test
    void sinPromocionesElPrecioEsElDeLista() {
        var vigentes = new CalculadorDePrecios.PromocionesVigentes(List.of(), AHORA);

        var precio = vigentes.precioDe(producto(1L, 100_000, marca(5L)));

        assertFalse(precio.tienePromocion());
        assertEquals(new BigDecimal("100000"), precio.precio().monto());
        assertEquals(BigDecimal.ZERO.setScale(0), precio.descuento().monto());
        assertNull(precio.promocion());
    }

    @Test
    void unaPromocionDeTodoElCatalogoDescuentaElPorcentaje() {
        var vigentes = new CalculadorDePrecios.PromocionesVigentes(
                List.of(promocion(1L, "20% en todo", 20, AlcancePromocion.TODOS, null, null)), AHORA);

        var precio = vigentes.precioDe(producto(1L, 100_000, marca(5L)));

        assertEquals(new BigDecimal("80000"), precio.precio().monto());
        assertEquals(new BigDecimal("20000"), precio.descuento().monto());
        assertEquals(new BigDecimal("100000"), precio.precioLista().monto());
        assertEquals("20% en todo", precio.promocion().nombre());
    }

    @Test
    void laPromocionDeUnProductoNoAlcanzaALosDemas() {
        var vigentes = new CalculadorDePrecios.PromocionesVigentes(
                List.of(promocion(1L, "Solo el 7", 50, AlcancePromocion.PRODUCTO, producto(7L, 1, null), null)),
                AHORA);

        assertTrue(vigentes.precioDe(producto(7L, 100_000, null)).tienePromocion());
        assertFalse(vigentes.precioDe(producto(8L, 100_000, null)).tienePromocion());
    }

    @Test
    void laPromocionDeUnaMarcaAlcanzaASusProductos() {
        Marca amd = marca(5L);
        var vigentes = new CalculadorDePrecios.PromocionesVigentes(
                List.of(promocion(1L, "AMD", 10, AlcancePromocion.MARCA, null, amd)), AHORA);

        assertTrue(vigentes.precioDe(producto(7L, 100_000, amd)).tienePromocion());
        assertFalse(vigentes.precioDe(producto(8L, 100_000, marca(6L))).tienePromocion());
        assertFalse(vigentes.precioDe(producto(9L, 100_000, null)).tienePromocion(),
                "un producto sin marca no puede entrar en una promoción de marca");
    }

    @Test
    void cuandoDosPromocionesSePisanGanaLaMejorYNoSeSuman() {
        Marca amd = marca(5L);
        Producto cpu = producto(7L, 100_000, amd);
        var vigentes = new CalculadorDePrecios.PromocionesVigentes(
                List.of(
                        promocion(1L, "Todo 10%", 10, AlcancePromocion.TODOS, null, null),
                        promocion(2L, "AMD 30%", 30, AlcancePromocion.MARCA, null, amd)),
                AHORA);

        var precio = vigentes.precioDe(cpu);

        assertEquals(new BigDecimal("70000"), precio.precio().monto(),
                "sumadas darían 60.000; se aplica solo la mayor");
        assertEquals("AMD 30%", precio.promocion().nombre());
    }

    @Test
    void elDescuentoSeRedondeaALaMonedaYNuncaDejaElProductoEnCero() {
        // El guaraní no tiene decimales: 33% de 999 son 329,67 y se redondea.
        var vigentes = new CalculadorDePrecios.PromocionesVigentes(
                List.of(promocion(1L, "33%", 33, AlcancePromocion.TODOS, null, null)), AHORA);

        var precio = vigentes.precioDe(producto(1L, 999, null));

        assertEquals(new BigDecimal("330"), precio.descuento().monto());
        assertEquals(new BigDecimal("669"), precio.precio().monto());
    }

    @Test
    void unProductoDeUnGuaraniNoPuedeQuedarGratis() {
        // 90% de 1 redondea a 1, y un descuento igual al precio rompe la
        // factura: en ese borde se cobra el precio de lista.
        var vigentes = new CalculadorDePrecios.PromocionesVigentes(
                List.of(promocion(1L, "90%", 90, AlcancePromocion.TODOS, null, null)), AHORA);

        var precio = vigentes.precioDe(producto(1L, 1, null));

        assertFalse(precio.tienePromocion());
        assertEquals(new BigDecimal("1"), precio.precio().monto());
    }

    @Test
    void laVentanaDeFechasDecideSiRige() {
        Promocion promocion = promocion(1L, "Fin de semana", 20, AlcancePromocion.TODOS, null, null);
        promocion.setDesde(AHORA.plusDays(1));
        promocion.setHasta(AHORA.plusDays(3));

        assertFalse(promocion.vigenteEn(AHORA), "todavía no empezó");
        assertTrue(promocion.vigenteEn(AHORA.plusDays(2)));
        assertFalse(promocion.vigenteEn(AHORA.plusDays(4)), "ya terminó");
    }

    @Test
    void apagadaNoRigeAunqueEsteDentroDeLaVentana() {
        Promocion promocion = promocion(1L, "Apagada", 20, AlcancePromocion.TODOS, null, null);
        promocion.setActiva(false);

        assertFalse(promocion.vigenteEn(AHORA));
    }

    private static Producto producto(Long id, long precio, Marca marca) {
        Producto producto = new Producto();
        producto.setId(id);
        producto.setMoneda(Moneda.PYG);
        producto.setPrecio(Dinero.de(precio, Moneda.PYG));
        producto.setMarca(marca);
        return producto;
    }

    private static Marca marca(Long id) {
        Marca marca = new Marca();
        marca.setId(id);
        marca.setNombre("Marca " + id);
        return marca;
    }

    private static Promocion promocion(
            Long id, String nombre, int porcentaje, AlcancePromocion alcance, Producto producto, Marca marca) {
        Promocion promocion = new Promocion();
        promocion.setId(id);
        promocion.setNombre(nombre);
        promocion.setPorcentaje(BigDecimal.valueOf(porcentaje));
        promocion.setAlcance(alcance);
        promocion.setProducto(producto);
        promocion.setMarca(marca);
        promocion.setDesde(AHORA.minusDays(1));
        promocion.setHasta(AHORA.plusDays(1));
        promocion.setActiva(true);
        return promocion;
    }
}
