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

    private String login() throws Exception {
        String response = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@techstore.py\",\"password\":\"admin123\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(response).path("accessToken").asText();
    }
}
