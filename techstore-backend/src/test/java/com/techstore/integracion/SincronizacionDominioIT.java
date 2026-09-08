package com.techstore.integracion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.techstore.comun.tenant.TenantContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Outbox de sincronización de dominio")
class SincronizacionDominioIT {
    @DynamicPropertySource
    static void base(DynamicPropertyRegistry registry) {
        BaseDatosDePrueba.registrar(registry);
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;

    @Test
    void cambioDeStockSeRecuperaPorCursorDesdeElMismoTenant() throws Exception {
        String token = login();
        Long productId = TenantContext.comoTenant(1L,
                () -> jdbc.queryForObject("SELECT id FROM productos ORDER BY id LIMIT 1", Long.class));
        Long cursor = TenantContext.comoTenant(1L,
                () -> jdbc.queryForObject("SELECT COALESCE(MAX(id),0) FROM domain_event_outbox", Long.class));

        TenantContext.comoTenant(1L, () -> {
            jdbc.update("UPDATE productos SET stock_real=stock_real+1 WHERE id=?", productId);
            return null;
        });

        String response = mvc.perform(get("/api/v1/sync/events")
                        .param("after", String.valueOf(cursor))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode events = mapper.readTree(response);
        assertTrue(events.valueStream().anyMatch(event -> "STOCK_UPDATED".equals(event.path("eventType").asText())
                && productId.equals(event.path("aggregateId").asLong())));

        TenantContext.comoTenant(1L, () -> {
            jdbc.update("UPDATE productos SET stock_real=stock_real-1 WHERE id=?", productId);
            return null;
        });
    }

    @Test
    void entidadSinIdNumericoTambienGeneraEventoRecuperable() throws Exception {
        String token = login();
        Long cursor = TenantContext.comoTenant(1L,
                () -> jdbc.queryForObject("SELECT COALESCE(MAX(id),0) FROM domain_event_outbox", Long.class));

        TenantContext.comoTenant(1L, () -> {
            jdbc.update("UPDATE configuracion SET valor=valor WHERE clave='smtp.host'");
            return null;
        });

        String response = mvc.perform(get("/api/v1/sync/events")
                        .param("after", String.valueOf(cursor))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode events = mapper.readTree(response);
        assertTrue(events.valueStream().anyMatch(event -> "CONFIGURATION_UPDATED".equals(
                        event.path("eventType").asText())
                && event.path("aggregateId").asLong() == 0));
    }

    /**
     * Agotarse, volver a haber y cambiar de precio son tres cosas distintas.
     *
     * <p>Antes tocar un producto emitía siempre lo mismo —{@code PRODUCT_UPDATED}—
     * y, si se movía el stock, un {@code STOCK_UPDATED}. Con eso alcanza para
     * volver a pedir la ficha, pero no para reaccionar a algo puntual: avisarle a
     * quien lo tenía en favoritos que volvió a haber obliga a comparar contra lo
     * anterior, y eso solo lo sabe el disparador.
     *
     * <p>«Disponible» es el stock real menos el comprometido: un producto con
     * diez unidades y diez reservadas está agotado para quien entra a comprar.
     */
    @Test
    @DisplayName("agotarse, reponerse y cambiar de precio emiten su propio evento")
    void transicionesDelProductoEmitenEventosPropios() {
        Long productId = TenantContext.comoTenant(1L,
                () -> jdbc.queryForObject("SELECT id FROM productos ORDER BY id LIMIT 1", Long.class));
        Integer stockOriginal = TenantContext.comoTenant(1L,
                () -> jdbc.queryForObject("SELECT stock_real FROM productos WHERE id=?", Integer.class, productId));
        BigDecimal precioOriginal = TenantContext.comoTenant(1L,
                () -> jdbc.queryForObject("SELECT precio FROM productos WHERE id=?", BigDecimal.class, productId));
        try {
            TenantContext.comoTenant(1L, () -> {
                jdbc.update("UPDATE productos SET stock_real=5, stock_comprometido=0 WHERE id=?", productId);
                return null;
            });

            long antesDeAgotar = ultimoEvento();
            TenantContext.comoTenant(1L, () -> {
                jdbc.update("UPDATE productos SET stock_real=0 WHERE id=?", productId);
                return null;
            });
            assertTrue(emitidos(antesDeAgotar, productId).contains("PRODUCT_OUT_OF_STOCK"),
                    "quedarse sin unidades tiene que avisarse aparte del cambio de stock");

            long antesDeReponer = ultimoEvento();
            TenantContext.comoTenant(1L, () -> {
                jdbc.update("UPDATE productos SET stock_real=3 WHERE id=?", productId);
                return null;
            });
            assertTrue(emitidos(antesDeReponer, productId).contains("PRODUCT_BACK_IN_STOCK"),
                    "volver a haber es la noticia que espera quien lo tenía en favoritos");

            long antesDelPrecio = ultimoEvento();
            TenantContext.comoTenant(1L, () -> {
                jdbc.update("UPDATE productos SET precio=? WHERE id=?", new BigDecimal("123456.00"), productId);
                return null;
            });
            assertTrue(emitidos(antesDelPrecio, productId).contains("PRICE_UPDATED"),
                    "un cambio de precio no se distingue de cualquier otra edición sin su evento");
            String carga = TenantContext.comoTenant(1L, () -> jdbc.queryForObject(
                    "SELECT payload::text FROM domain_event_outbox WHERE id > ? AND aggregate_id = ?"
                            + " AND event_type = 'PRICE_UPDATED' ORDER BY id LIMIT 1",
                    String.class, antesDelPrecio, productId));
            assertTrue(carga.contains("precioAnterior"),
                    "el evento tiene que traer el precio de antes; si no, no se puede decir si subió o bajó: " + carga);
        } finally {
            TenantContext.comoTenant(1L, () -> {
                jdbc.update("UPDATE productos SET stock_real=?, precio=? WHERE id=?",
                        stockOriginal, precioOriginal, productId);
                return null;
            });
        }
    }

    /**
     * Un producto agotado por reservas, sin que se mueva una sola unidad del
     * depósito. Es el caso que se escapa si se mira solo {@code stock_real}.
     */
    @Test
    @DisplayName("reservar hasta la última unidad también cuenta como agotado")
    void comprometerTodoElStockTambienAgota() {
        Long productId = TenantContext.comoTenant(1L,
                () -> jdbc.queryForObject("SELECT id FROM productos ORDER BY id LIMIT 1", Long.class));
        Integer stockOriginal = TenantContext.comoTenant(1L,
                () -> jdbc.queryForObject("SELECT stock_real FROM productos WHERE id=?", Integer.class, productId));
        try {
            TenantContext.comoTenant(1L, () -> {
                jdbc.update("UPDATE productos SET stock_real=4, stock_comprometido=0 WHERE id=?", productId);
                return null;
            });
            long antes = ultimoEvento();
            TenantContext.comoTenant(1L, () -> {
                jdbc.update("UPDATE productos SET stock_comprometido=4 WHERE id=?", productId);
                return null;
            });

            assertTrue(emitidos(antes, productId).contains("PRODUCT_OUT_OF_STOCK"),
                    "con todo reservado no queda nada que vender, aunque el depósito esté lleno");
        } finally {
            TenantContext.comoTenant(1L, () -> {
                jdbc.update("UPDATE productos SET stock_real=?, stock_comprometido=0 WHERE id=?",
                        stockOriginal, productId);
                return null;
            });
        }
    }

    private long ultimoEvento() {
        return TenantContext.comoTenant(1L,
                () -> jdbc.queryForObject("SELECT COALESCE(MAX(id),0) FROM domain_event_outbox", Long.class));
    }

    private List<String> emitidos(long desde, Long productoId) {
        return TenantContext.comoTenant(1L, () -> jdbc.queryForList(
                "SELECT event_type FROM domain_event_outbox WHERE id > ? AND aggregate_id = ?"
                        + " AND aggregate_type = 'PRODUCT' ORDER BY id",
                String.class, desde, productoId));
    }

    private String login() throws Exception {
        String response = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@techstore.py\",\"password\":\"admin123\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(response).path("accessToken").asText();
    }
}
