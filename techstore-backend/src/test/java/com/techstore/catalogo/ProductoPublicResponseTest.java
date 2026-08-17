package com.techstore.catalogo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.techstore.comun.Dinero;
import com.techstore.comun.Moneda;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductoPublicResponseTest {

    private final ProductoMapper mapper = new ProductoMapperImpl();
    private final ObjectMapper json = new ObjectMapper();

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
}
