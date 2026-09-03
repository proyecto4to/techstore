package com.techstore.catalogo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.techstore.comun.Dinero;
import com.techstore.comun.Moneda;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * El contrato del mostrador tiene que dar el stock real y nada mas: el cajero
 * necesita saber cuanto puede vender, no cuanto gana la tienda con cada venta.
 */
class ProductoOperativoResponseTest {

    private final ProductoMapper mapper = new ProductoMapperImpl();
    private final ObjectMapper json = new ObjectMapper();

    private Producto producto() {
        Producto producto = new Producto();
        producto.setId(11L);
        producto.setNombre("Monitor 27 pulgadas");
        producto.setPrecio(Dinero.de(2_000_000, Moneda.PYG));
        producto.registrarCosto(Dinero.de(1_200_000, Moneda.PYG), java.time.LocalDateTime.now());
        producto.setIva(BigDecimal.TEN);
        producto.setStockReal(40);
        producto.setStockComprometido(4);
        producto.setStockMinimo(3);
        producto.setActivo(true);
        return producto;
    }

    @Test
    void informaElInventarioRealSinTecho() {
        // A diferencia de la vidriera, no se techa en 99: una venta de
        // mostrador puede ser por mas unidades y el cajero necesita el numero.
        ProductoOperativoResponse respuesta = mapper.toOperativoResponse(producto());

        assertEquals(40, respuesta.stockReal());
        assertEquals(36, respuesta.stockDisponible());
        assertEquals("Monitor 27 pulgadas", respuesta.nombre());
    }

    @Test
    void noRevelaLaRentabilidad() {
        JsonNode respuesta = json.valueToTree(mapper.toOperativoResponse(producto()));

        for (String campo : new String[]{"costo", "costoActualizadoEn", "margen",
                "stockComprometido", "stockMinimo", "necesitaReposicion", "ocultarSinStock",
                "activo", "createdAt", "pesoKg"}) {
            assertFalse(respuesta.has(campo), "El contrato operativo filtró el campo interno " + campo);
        }
    }
}
