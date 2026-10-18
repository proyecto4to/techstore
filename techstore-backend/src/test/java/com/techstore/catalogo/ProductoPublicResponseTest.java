package com.techstore.catalogo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.techstore.comun.Dinero;
import com.techstore.comun.Moneda;
import com.techstore.promociones.AlcancePromocion;
import com.techstore.promociones.CalculadorDePrecios;
import com.techstore.promociones.Promocion;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductoPublicResponseTest {

    private final ProductoMapper mapper = new ProductoMapperImpl();
    // Con el módulo de fechas, como el ObjectMapper que arma Spring Boot: sin
    // él, serializar la fecha de fin de una promoción falla acá y en ningún
    // otro lado, que es una diferencia del test y no del contrato.
    private final ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void elContratoPublicoNoSerializaDatosInternos() {
        Producto producto = new Producto();
        producto.setId(7L);
        producto.setNombre("Notebook segura");
        producto.setPrecio(Dinero.de(5_000_000, Moneda.PYG));
        producto.registrarCosto(Dinero.de(3_000_000, Moneda.PYG), java.time.LocalDateTime.now());
        producto.setIva(BigDecimal.TEN);
        producto.setStockReal(10);
        producto.setStockComprometido(3);
        producto.setStockMinimo(2);
        producto.setActivo(true);

        JsonNode respuesta = json.valueToTree(mapper.toPublicResponse(producto));

        assertTrue(respuesta.path("disponible").asBoolean());
        for (String campo : new String[]{"costo", "costoActualizadoEn", "margen", "stockReal",
                "stockComprometido", "stockDisponible", "stockMinimo", "necesitaReposicion",
                "ocultarSinStock", "activo", "createdAt"}) {
            assertFalse(respuesta.has(campo), "El DTO público filtró el campo interno " + campo);
        }
    }

    @Test
    void maxPorPedidoReflejaLoQueQuedaCuandoEsPoco() {
        Producto producto = new Producto();
        producto.setStockReal(10);
        producto.setStockComprometido(2);

        assertEquals(8, mapper.toPublicResponse(producto).maxPorPedido());
    }

    @Test
    void maxPorPedidoNoDelataElTamanoDelDeposito() {
        Producto producto = new Producto();
        producto.setStockReal(5_000);
        producto.setStockComprometido(0);

        assertEquals(Producto.MAXIMO_POR_PEDIDO, mapper.toPublicResponse(producto).maxPorPedido());
    }

    @Test
    void elPrecioPublicadoEsElQueSeVaACobrar() {
        // La regla que protege a las apps viejas: el descuento entra en
        // `precio`, no solo en `promocion`. Una versión que no conoce las
        // promociones muestra este número, y muestra el correcto.
        Producto producto = new Producto();
        producto.setId(7L);
        producto.setPrecio(Dinero.de(100_000, Moneda.PYG));
        producto.setStockReal(5);

        Promocion promocion = new Promocion();
        promocion.setId(3L);
        promocion.setNombre("Liquidación");
        promocion.setPorcentaje(new BigDecimal("20"));
        promocion.setAlcance(AlcancePromocion.TODOS);
        promocion.setActiva(true);
        promocion.setDesde(LocalDateTime.now().minusHours(1));
        promocion.setHasta(LocalDateTime.now().plusHours(1));

        var vigentes = new CalculadorDePrecios.PromocionesVigentes(
                List.of(promocion), LocalDateTime.now());
        JsonNode respuesta = json.valueToTree(
                mapper.toPublicResponse(producto).con(vigentes.precioDe(producto)));

        assertEquals(80_000, respuesta.path("precio").asLong());
        assertEquals(100_000, respuesta.path("precioLista").asLong());
        assertEquals("Liquidación", respuesta.path("promocion").path("nombre").asText());
    }

    @Test
    void sinPromocionElContratoNoInventaUnPrecioAnterior() {
        Producto producto = new Producto();
        producto.setPrecio(Dinero.de(100_000, Moneda.PYG));
        producto.setStockReal(5);

        var vigentes = new CalculadorDePrecios.PromocionesVigentes(
                List.of(), LocalDateTime.now());
        JsonNode respuesta = json.valueToTree(
                mapper.toPublicResponse(producto).con(vigentes.precioDe(producto)));

        assertEquals(100_000, respuesta.path("precio").asLong());
        assertTrue(respuesta.path("precioLista").isNull());
        assertTrue(respuesta.path("promocion").isNull());
    }

    @Test
    void maxPorPedidoNuncaEsNegativoAunqueLoComprometidoSupereElStock() {
        Producto producto = new Producto();
        producto.setStockReal(2);
        producto.setStockComprometido(5);

        ProductoPublicResponse respuesta = mapper.toPublicResponse(producto);
        assertEquals(0, respuesta.maxPorPedido());
        assertFalse(respuesta.disponible());
    }
}
