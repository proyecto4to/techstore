package com.techstore.integracion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * El flujo de eventos de dominio dice qué caché invalidar, no qué pasa en el
 * negocio.
 *
 * <p>Estaba abierto a cualquier sesión: con un token de rol Cliente se leían los
 * movimientos y cierres de caja, los pagos y las facturas, las direcciones de
 * otros clientes y el alta y baja de cada usuario. Se reprodujo contra el
 * servidor real antes de arreglarlo.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("El flujo de eventos se acota por rol")
class FlujoDeEventosPorRolIT {

    /** Lo que no puede salir de la operación de la tienda. */
    private static final List<String> AGREGADOS_DE_OPERACION = List.of(
            "CASH_MOVEMENT", "CASH_OPENING", "CASH_CLOSING",
            "PAYMENT", "INVOICE", "CREDIT_NOTE",
            "USER", "DELIVERY_ADDRESS", "SUPPLIER", "PURCHASE_DOCUMENT");

    @DynamicPropertySource
    static void base(DynamicPropertyRegistry registry) {
        BaseDatosDePrueba.registrar(registry);
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void alClienteNoLeLleganLosEventosDeLaOperacion() throws Exception {
        sembrarEventosDeOperacion();
        String cliente = nuevoCliente("eventos.cliente@techstore.py");

        Set<String> recibidos = agregadosVistosPor(cliente);

        for (String prohibido : AGREGADOS_DE_OPERACION) {
            assertFalse(recibidos.contains(prohibido),
                    "El flujo le entregó eventos de " + prohibido + " a un cliente");
        }
    }

    @Test
    void alClienteSiLeLleganLosDeSuCompraYDelCatalogo() throws Exception {
        sembrarEventosDeOperacion();
        String cliente = nuevoCliente("eventos.cliente.dos@techstore.py");

        Set<String> recibidos = agregadosVistosPor(cliente);

        // Sin estos, el catálogo y el estado del pedido dejarían de refrescarse
        // solos: la restricción no puede romper lo que el cliente sí necesita.
        assertTrue(recibidos.contains("PRODUCT") || recibidos.contains("ORDER"),
                "El cliente se quedó sin los eventos que refrescan su compra");
    }

    @Test
    void administracionSiVeElFlujoCompleto() throws Exception {
        sembrarEventosDeOperacion();
        String admin = login("admin@techstore.py", "admin123");

        Set<String> recibidos = agregadosVistosPor(admin);

        assertTrue(recibidos.contains("USER"),
                "Administración perdió eventos que necesita; la restricción quedó demasiado ancha");
    }

    /**
     * Deja en la bitácora al menos un evento de cada agregado prohibido.
     *
     * <p>Se escribe directo en la tabla porque lo que se prueba es el filtro de
     * lectura, no cómo se generan los eventos.
     */
    private void sembrarEventosDeOperacion() {
        List<String> todos = new ArrayList<>(AGREGADOS_DE_OPERACION);
        todos.add("PRODUCT");
        todos.add("ORDER");
        for (String agregado : todos) {
            jdbc.update("""
                    INSERT INTO domain_event_outbox
                        (tenant_id, event_type, aggregate_type, aggregate_id, payload, status, occurred_at)
                    VALUES (1, ?, ?, 1, '{}'::jsonb, 'PROCESSED', CURRENT_TIMESTAMP)
                    """, agregado + "_CREATED", agregado);
        }
    }

    /** Todos los agregados que ese token llega a ver recorriendo el cursor. */
    private Set<String> agregadosVistosPor(String token) throws Exception {
        Set<String> vistos = new HashSet<>();
        long cursor = 0;
        for (int pagina = 0; pagina < 20; pagina++) {
            MvcResult resultado = mvc.perform(get("/api/v1/sync/events")
                            .param("after", String.valueOf(cursor))
                            .param("limit", "200")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andReturn();
            JsonNode eventos = json.readTree(resultado.getResponse().getContentAsString());
            if (eventos.isEmpty()) {
                break;
            }
            for (JsonNode evento : eventos) {
                vistos.add(evento.path("aggregateType").asText());
                cursor = Math.max(cursor, evento.path("id").asLong());
            }
        }
        return vistos;
    }

    private String nuevoCliente(String email) throws Exception {
        mvc.perform(post("/api/v1/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Eventos\",\"apellido\":\"Cliente\",\"email\":\"" + email
                                + "\",\"password\":\"Cliente123\"}"))
                .andExpect(status().isCreated());
        return login(email, "Cliente123");
    }

    private String login(String email, String password) throws Exception {
        MvcResult resultado = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return json.readTree(resultado.getResponse().getContentAsString()).path("accessToken").asText();
    }
}
