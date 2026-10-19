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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Catálogo público y favoritos por HTTP")
class CatalogoPublicoIT {

    @DynamicPropertySource
    static void base(DynamicPropertyRegistry registro) {
        BaseDatosDePrueba.registrar(registro);
    }

    @Autowired
    private MockMvc mvc;

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void sirveLasFotosEmpaquetadasDelCatalogoSinAutenticacion() throws Exception {
        mvc.perform(get("/img/productos/cpu.jpg"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.IMAGE_JPEG))
                // Con "no-cache" la tienda y la app volvian a bajar cada foto
                // en cada visita.
                .andExpect(header().string("Cache-Control", "max-age=604800, public"));
    }

    @Test
    void noFiltraDatosInternosYPermiteGestionarMisFavoritos() throws Exception {
        MvcResult catalogo = mvc.perform(get("/api/v1/productos").param("size", "1"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode producto = json.readTree(catalogo.getResponse().getContentAsString())
                .path("content").path(0);

        assertTrue(producto.path("id").canConvertToLong());
        assertTrue(producto.has("disponible"));
        for (String interno : new String[]{"costo", "margen", "stockReal", "stockComprometido",
                "stockDisponible", "stockMinimo", "necesitaReposicion", "activo"}) {
            assertFalse(producto.has(interno), "La API pública expuso " + interno);
        }

        long productoId = producto.path("id").asLong();
        String accessToken = login();

        mvc.perform(post("/api/v1/favoritos/{id}", productoId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isCreated());

        MvcResult favoritos = mvc.perform(get("/api/v1/favoritos")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode lista = json.readTree(favoritos.getResponse().getContentAsString());
        assertTrue(lista.findValuesAsText("id").contains(String.valueOf(productoId)));

        mvc.perform(delete("/api/v1/favoritos/{id}", productoId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNoContent());
    }

    @Test
    void revalidaUnCarritoAnonimoConPrecioCanonicoSinExponerStock() throws Exception {
        MvcResult catalogo = mvc.perform(get("/api/v1/productos").param("size", "1"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode producto = json.readTree(catalogo.getResponse().getContentAsString())
                .path("content").path(0);

        MvcResult validacion = mvc.perform(post("/api/v1/carrito/validar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[{"productoId":%d,"cantidad":1}]}
                                """.formatted(producto.path("id").asLong())))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode respuesta = json.readTree(validacion.getResponse().getContentAsString());
        JsonNode linea = respuesta.path("items").path(0);
        assertTrue(respuesta.path("valido").asBoolean());
        assertTrue(linea.path("estado").asText().equals("DISPONIBLE"));
        assertTrue(linea.path("producto").path("precio").decimalValue()
                .compareTo(producto.path("precio").decimalValue()) == 0);
        assertFalse(linea.path("producto").has("stockDisponible"));
        assertFalse(linea.path("producto").has("stockReal"));
    }

    private String login() throws Exception {
        MvcResult resultado = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"admin@techstore.py","password":"admin123"}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        return json.readTree(resultado.getResponse().getContentAsString()).path("accessToken").asText();
    }
}
