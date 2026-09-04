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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Los permisos por rol, aplicados de verdad sobre la API.
 *
 * <p>Lo que hace que esto sea seguridad y no maquillaje es que la regla se
 * comprueba antes de llegar al controlador: quitar un permiso cierra el
 * endpoint aunque el cliente lo llame a mano.
 *
 * <p>El caso más importante es el inverso: <b>tildar una casilla no abre nada</b>
 * que las anotaciones del backend nieguen. Sin ese límite, la pantalla de
 * permisos sería una forma de saltearse las reglas de negocio desde una grilla.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Permisos por rol")
class PermisosPorRolIT {

    @DynamicPropertySource
    static void base(DynamicPropertyRegistry registry) {
        BaseDatosDePrueba.registrar(registry);
    }

    @Autowired MockMvc mvc;
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void quitarleUnPermisoAlCajeroLeCierraElEndpoint() throws Exception {
        String admin = login("admin@techstore.py", "admin123");
        String cajero = nuevoCajero("permisos.quitar@techstore.py");

        // Con la siembra, el cajero puede ver el catálogo operativo.
        mvc.perform(get("/api/v1/productos/operacion")
                        .param("size", "1")
                        .header("Authorization", bearer(cajero)))
                .andExpect(status().isOk());

        cambiarPermiso(admin, "Cajero", "PRODUCTOS", "VER", false);

        // La restricción se aplica en el servidor, no en la pantalla.
        mvc.perform(get("/api/v1/productos/operacion")
                        .param("size", "1")
                        .header("Authorization", bearer(cajero)))
                .andExpect(status().isForbidden());

        cambiarPermiso(admin, "Cajero", "PRODUCTOS", "VER", true);

        mvc.perform(get("/api/v1/productos/operacion")
                        .param("size", "1")
                        .header("Authorization", bearer(cajero)))
                .andExpect(status().isOk());
    }

    @Test
    void darleUnPermisoNoLeAbreLoQueElBackendNiega() throws Exception {
        String admin = login("admin@techstore.py", "admin123");
        String cajero = nuevoCajero("permisos.abrir@techstore.py");

        // Aunque se le habilite USUARIOS en la matriz...
        cambiarPermiso(admin, "Cajero", "USUARIOS", "VER", true);

        // ...el @PreAuthorize del controlador sigue siendo el piso.
        mvc.perform(get("/api/v1/usuarios").header("Authorization", bearer(cajero)))
                .andExpect(status().isForbidden());
    }

    @Test
    void soloAdministracionEntraAlModuloDeSeguridad() throws Exception {
        String cajero = nuevoCajero("permisos.modulo@techstore.py");

        // Quien reparte permisos podría dárselos a sí mismo: este módulo tiene
        // que ser el más cerrado de todos.
        mvc.perform(get("/api/v1/seguridad/roles").header("Authorization", bearer(cajero)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/seguridad/roles/Cajero/permisos")
                        .header("Authorization", bearer(cajero)))
                .andExpect(status().isForbidden());
    }

    @Test
    void laMatrizTraeTodosLosModulosConSuNombreEnCastellano() throws Exception {
        String admin = login("admin@techstore.py", "admin123");

        MvcResult resultado = mvc.perform(get("/api/v1/seguridad/roles/Cajero/permisos")
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode matriz = json.readTree(resultado.getResponse().getContentAsString());
        JsonNode modulos = matriz.path("modulos");
        assertTrue(modulos.isArray() && !modulos.isEmpty(), "La matriz vino vacía");
        for (JsonNode modulo : modulos) {
            assertFalse(modulo.path("etiqueta").asText().isBlank(),
                    "Un módulo llegó sin nombre para mostrar");
            assertTrue(modulo.path("acciones").size() == 4,
                    "Cada módulo tiene que traer ver, crear, editar y eliminar");
        }
        assertTrue(matriz.path("pantallas").isArray(),
                "La configuración de pantallas por rol tiene que venir con la matriz");
    }

    @Test
    void laConfiguracionDePantallasSeGuardaYSeLee() throws Exception {
        String admin = login("admin@techstore.py", "admin123");

        MvcResult resultado = mvc.perform(put("/api/v1/seguridad/roles/Repartidor/pantallas")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pantalla\":\"caja\",\"permitido\":false}"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode pantallas = json.readTree(resultado.getResponse().getContentAsString())
                .path("pantallas");
        boolean encontrada = false;
        for (JsonNode pantalla : pantallas) {
            if ("caja".equals(pantalla.path("pantalla").asText())) {
                encontrada = true;
                assertFalse(pantalla.path("permitido").asBoolean(),
                        "El cambio no quedó guardado");
            }
        }
        assertTrue(encontrada, "La pantalla configurada no volvió en la matriz");
    }

    private void cambiarPermiso(String admin, String rol, String modulo, String accion, boolean permitido)
            throws Exception {
        mvc.perform(put("/api/v1/seguridad/roles/" + rol + "/permisos")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"modulo\":\"" + modulo + "\",\"accion\":\"" + accion
                                + "\",\"permitido\":" + permitido + "}"))
                .andExpect(status().isOk());
    }

    private String nuevoCajero(String email) throws Exception {
        String admin = login("admin@techstore.py", "admin123");
        mvc.perform(post("/api/v1/usuarios")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Caja\",\"apellido\":\"Permisos\",\"email\":\"" + email
                                + "\",\"password\":\"Cajero123\",\"rol\":\"Cajero\"}"))
                .andExpect(status().is2xxSuccessful());
        return login(email, "Cajero123");
    }

    private String bearer(String token) {
        return "Bearer " + token;
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
