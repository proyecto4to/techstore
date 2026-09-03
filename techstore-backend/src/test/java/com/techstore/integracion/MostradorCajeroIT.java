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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Lo que un cajero puede ver del catálogo para atender el mostrador.
 *
 * Existe por un bug concreto: la pantalla de venta directa pedía la lista de
 * productos al endpoint de administración, que es solo para Admin, así que a un
 * cajero le devolvía 403 y no podía vender. Estos casos fijan las dos mitades
 * de la solución: que ahora sí pueda listar para operar, y que al hacerlo no
 * reciba la rentabilidad del negocio.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Mostrador del cajero")
class MostradorCajeroIT {

    @DynamicPropertySource
    static void base(DynamicPropertyRegistry registry) {
        BaseDatosDePrueba.registrar(registry);
    }

    @Autowired MockMvc mvc;
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void elCajeroListaParaVenderPeroNoVeCostoNiMargen() throws Exception {
        String admin = login("admin@techstore.py", "admin123");
        createUser(admin, "Caja", "Mostrador", "caja.mostrador@techstore.py", "Cajero123", "Cajero");
        String cashier = login("caja.mostrador@techstore.py", "Cajero123");

        // El listado de administración le sigue estando negado: expone el costo
        // y el margen de cada producto.
        mvc.perform(get("/api/v1/productos/admin").header("Authorization", bearer(cashier)))
                .andExpect(status().isForbidden());

        // El operativo, que es el que usa la venta directa, sí lo puede leer.
        MvcResult result = mvc.perform(get("/api/v1/productos/operacion")
                        .param("size", "5")
                        .header("Authorization", bearer(cashier)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode contenido = json.readTree(result.getResponse().getContentAsString()).path("content");
        assertTrue(contenido.isArray(), "El listado operativo tiene que traer una página de productos");
        assertFalse(contenido.isEmpty(), "El catálogo semilla debería traer productos para vender");

        JsonNode primero = contenido.get(0);
        assertTrue(primero.has("stockDisponible"),
                "Sin el stock disponible el cajero no sabe cuánto puede vender");
        assertTrue(primero.has("stockReal"),
                "El inventario físico lo necesita la carga de compras");
        for (String prohibido : new String[]{"costo", "margen", "costoActualizadoEn"}) {
            assertFalse(primero.has(prohibido),
                    "El listado operativo le filtró " + prohibido + " a un cajero");
        }
    }

    @Test
    void elClienteNoPuedeLeerElListadoOperativo() throws Exception {
        register("cliente.mostrador@techstore.py");
        String customer = login("cliente.mostrador@techstore.py", "Cliente123");

        mvc.perform(get("/api/v1/productos/operacion").header("Authorization", bearer(customer)))
                .andExpect(status().isForbidden());
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

    private void register(String email) throws Exception {
        mvc.perform(post("/api/v1/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Cliente\",\"apellido\":\"Mostrador\",\"email\":\"" + email
                                + "\",\"password\":\"Cliente123\"}"))
                .andExpect(status().isCreated());
    }

    private void createUser(String token, String name, String lastName, String email,
                            String password, String role) throws Exception {
        mvc.perform(post("/api/v1/usuarios")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"" + name + "\",\"apellido\":\"" + lastName + "\",\"email\":\""
                                + email + "\",\"password\":\"" + password + "\",\"rol\":\"" + role + "\"}"))
                .andExpect(status().is2xxSuccessful());
    }
}
