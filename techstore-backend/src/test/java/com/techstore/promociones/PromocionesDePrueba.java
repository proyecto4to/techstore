package com.techstore.promociones;

import com.techstore.catalogo.Marca;
import com.techstore.catalogo.Producto;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Calculadores de precio armados a mano para los tests.
 *
 * <p>{@link #sinNinguna()} existe para que esta dependencia nueva no obligue a
 * cada caso de carrito, catálogo o pedidos a simular un repositorio más:
 * devuelve siempre el precio de lista, como se comportaba el sistema antes de
 * que las promociones existieran.
 */
public final class PromocionesDePrueba {

    private PromocionesDePrueba() {
    }

    /** Ninguna promoción cargada: el precio es el de lista. */
    public static CalculadorDePrecios sinNinguna() {
        return con();
    }

    /** Un calculador que ve exactamente estas promociones. */
    public static CalculadorDePrecios con(Promocion... promociones) {
        PromocionRepository repositorio = Mockito.mock(PromocionRepository.class);
        // Permisivo a propósito: los casos que no tocan precios nunca llegan a
        // preguntar por promociones, y con la verificación estricta de Mockito
        // ese stub sin usar haría fallar tests que no tienen nada que ver.
        Mockito.lenient()
                .when(repositorio.vigentesEn(Mockito.any(LocalDateTime.class)))
                .thenReturn(List.of(promociones));
        return new CalculadorDePrecios(repositorio);
    }

    /** Una promoción vigente ahora, del alcance que pida el caso. */
    public static Promocion vigente(
            String nombre, int porcentaje, AlcancePromocion alcance, Producto producto, Marca marca) {
        Promocion promocion = new Promocion();
        promocion.setId(1L);
        promocion.setNombre(nombre);
        promocion.setPorcentaje(BigDecimal.valueOf(porcentaje));
        promocion.setAlcance(alcance);
        promocion.setProducto(producto);
        promocion.setMarca(marca);
        promocion.setDesde(LocalDateTime.now().minusDays(1));
        promocion.setHasta(LocalDateTime.now().plusDays(1));
        promocion.setActiva(true);
        return promocion;
    }
}
