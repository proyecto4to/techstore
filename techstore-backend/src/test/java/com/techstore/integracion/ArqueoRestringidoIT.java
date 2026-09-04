package com.techstore.integracion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * El resultado del arqueo es información de control sobre el propio cajero.
 *
 * <p>Si lo ve mientras cuenta el efectivo, contar deja de ser una declaración y
 * pasa a ser cuadrar contra un número que ya conoce. Estos casos fijan que la
 * restricción viva en el servidor: ocultar el dato en la pantalla no impide
 * llamar al endpoint desde cualquier cliente.
 *
 * <p>Se comprueba de más que el efectivo teórico: como es exactamente
 * {@code saldoInicial + ingresosEfectivo - egresosEfectivo}, dejar pasar esos
 * totales equivaldría a entregarlo con una resta de por medio.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("El arqueo no llega al cajero")
class ArqueoRestringidoIT {

    /** Todo lo que permite deducir el monto esperado del turno. */
    private static final String[] CAMPOS_DE_ARQUEO = {
            "efectivoTeorico",
            "diferencia",
            "saldoInicial",
            "ingresosEfectivo",
            "egresosEfectivo",
            "ingresosTransferencia",
            "egresosTransferencia"
    };

    @DynamicPropertySource
    static void base(DynamicPropertyRegistry registry) {
        BaseDatosDePrueba.registrar(registry);
    }

    @Autowired MockMvc mvc;
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void laVistaPreviaDelCierreNoLeAdelantaElMontoEsperado() throws Exception {
        String cashier = nuevoCajero("previa");

        MvcResult resultado = mvc.perform(get("/api/v1/caja/cierres/preview")
                        .header("Authorization", bearer(cashier)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode previa = json.readTree(resultado.getResponse().getContentAsString());
        for (String campo : CAMPOS_DE_ARQUEO) {
            assertFalse(previa.has(campo),
                    "La vista previa le filtró " + campo + " a un cajero");
        }
        // Lo que sí necesita para declarar lo que tiene en el cajón.
        assertTrue(previa.has("moneda"), "El cajero necesita saber en qué moneda declara");
    }

    @Test
    void elHistorialDeCierresNoLeMuestraLaDiferencia() throws Exception {
        String cashier = nuevoCajero("historial");

        MvcResult resultado = mvc.perform(get("/api/v1/caja/cierres")
                        .param("size", "5")
                        .header("Authorization", bearer(cashier)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode contenido = json.readTree(resultado.getResponse().getContentAsString()).path("content");
        assertTrue(contenido.isArray(), "El historial tiene que venir paginado");
        for (JsonNode cierre : contenido) {
            for (String campo : CAMPOS_DE_ARQUEO) {
                assertFalse(cierre.has(campo),
                        "El historial de cierres le filtró " + campo + " a un cajero");
            }
        }
    }

    @Test
    void elResumenDeCajaEsSoloDeAdministracion() throws Exception {
        String cashier = nuevoCajero("resumen");

        // El resumen es literalmente el arqueo del período: su neto de efectivo
        // es el monto esperado.
        mvc.perform(get("/api/v1/caja/resumen").header("Authorization", bearer(cashier)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/caja/resumen/pdf").header("Authorization", bearer(cashier)))
                .andExpect(status().isForbidden());
    }

    @Test
    void administracionSiVeElArqueoCompleto() throws Exception {
        String admin = login("admin@techstore.py", "admin123");

        MvcResult resultado = mvc.perform(get("/api/v1/caja/cierres/preview")
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode previa = json.readTree(resultado.getResponse().getContentAsString());
        assertTrue(previa.has("efectivoTeorico"),
                "Administración sí tiene que ver el monto esperado; si no, la restricción rompió el control");

        mvc.perform(get("/api/v1/caja/resumen").header("Authorization", bearer(admin)))
                .andExpect(status().isOk());
    }

    private String nuevoCajero(String sufijo) throws Exception {
        String admin = login("admin@techstore.py", "admin123");
        String email = "caja.arqueo." + sufijo + "@techstore.py";
        mvc.perform(post("/api/v1/usuarios")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Caja\",\"apellido\":\"Arqueo\",\"email\":\"" + email
                                + "\",\"password\":\"Cajero123\",\"rol\":\"Cajero\"}"))
                .andExpect(status().is2xxSuccessful());
        return login(email, "Cajero123");
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private String login(String email, String password) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return json.readTree(result.getResponse().getContentAsString()).path("accessToken").asText();
    }
}
