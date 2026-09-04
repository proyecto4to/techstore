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
 * Sacar un producto de la vidriera sin sacarlo del negocio.
 *
 * <p>El filtro vive en la consulta y no en la pantalla: si estuviera solo en la
 * app, el producto seguiría llegando por la API y bastaría con pedir la ruta a
 * mano para verlo. Estos casos fijan que no aparezca en el listado, que no
 * salga por búsqueda y que su detalle responda como inexistente, y a la vez que
 * siga estando para administración y para vender por mostrador.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Visibilidad del producto en la app")
class VisibilidadEnAppIT {

    @DynamicPropertySource
    static void base(DynamicPropertyRegistry registry) {
        BaseDatosDePrueba.registrar(registry);
    }

    @Autowired MockMvc mvc;
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void ocultarloLoSacaDelCatalogoPeroNoDelNegocio() throws Exception {
        String admin = login("admin@techstore.py", "admin123");
        long id = crearProducto(admin, "Teclado Vidriera Test");

        // Recién creado se ofrece: es el valor por omisión.
        assertTrue(apareceEnCatalogo(id), "Un producto nuevo tiene que ofrecerse");
        mvc.perform(get("/api/v1/productos/" + id)).andExpect(status().isOk());

        ocultar(admin, id, true);

        assertFalse(apareceEnCatalogo(id), "El producto oculto seguía en el catálogo público");
        assertFalse(apareceEnBusqueda(id, "Vidriera"), "El producto oculto seguía saliendo por búsqueda");
        // Pedir la ruta a mano tampoco alcanza.
        mvc.perform(get("/api/v1/productos/" + id)).andExpect(status().isNotFound());

        // Pero sigue existiendo para administración y para el mostrador.
        assertTrue(apareceEnAdmin(admin, id), "Ocultarlo no puede borrarlo del panel");
        assertTrue(apareceEnOperativo(admin, id), "El mostrador tiene que poder venderlo igual");
    }

    @Test
    void volverAMostrarloLoDevuelveAlCatalogo() throws Exception {
        String admin = login("admin@techstore.py", "admin123");
        long id = crearProducto(admin, "Mouse Vidriera Test");

        ocultar(admin, id, true);
        assertFalse(apareceEnCatalogo(id));

        ocultar(admin, id, false);
        assertTrue(apareceEnCatalogo(id), "Volver a mostrarlo tiene que devolverlo al catálogo");
        mvc.perform(get("/api/v1/productos/" + id)).andExpect(status().isOk());
    }

    private long crearProducto(String token, String nombre) throws Exception {
        MvcResult resultado = mvc.perform(post("/api/v1/productos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"" + nombre + "\",\"precio\":150000,\"iva\":10,"
                                + "\"stockReal\":8,\"stockMinimo\":0,\"ocultarSinStock\":false}"))
                .andExpect(status().is2xxSuccessful())
                .andReturn();
        return json.readTree(resultado.getResponse().getContentAsString()).path("id").asLong();
    }

    private void ocultar(String token, long id, boolean oculto) throws Exception {
        mvc.perform(put("/api/v1/productos/" + id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Producto\",\"precio\":150000,\"iva\":10,"
                                + "\"stockReal\":8,\"stockMinimo\":0,\"ocultarSinStock\":false,"
                                + "\"visibleEnApp\":" + !oculto + "}"))
                .andExpect(status().isOk());
    }

    private boolean apareceEnCatalogo(long id) throws Exception {
        return contiene(get("/api/v1/productos").param("size", "200"), null, id);
    }

    private boolean apareceEnBusqueda(long id, String termino) throws Exception {
        return contiene(get("/api/v1/productos").param("search", termino).param("size", "200"), null, id);
    }

    private boolean apareceEnAdmin(String token, long id) throws Exception {
        return contiene(get("/api/v1/productos/admin").param("size", "200"), token, id);
    }

    private boolean apareceEnOperativo(String token, long id) throws Exception {
        return contiene(get("/api/v1/productos/operacion").param("size", "200"), token, id);
    }

    private boolean contiene(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder peticion,
            String token,
            long id) throws Exception {
        if (token != null) {
            peticion = peticion.header("Authorization", "Bearer " + token);
        }
        MvcResult resultado = mvc.perform(peticion).andExpect(status().isOk()).andReturn();
        JsonNode contenido = json.readTree(resultado.getResponse().getContentAsString()).path("content");
        for (JsonNode producto : contenido) {
            if (producto.path("id").asLong() == id) {
                return true;
            }
        }
        return false;
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
