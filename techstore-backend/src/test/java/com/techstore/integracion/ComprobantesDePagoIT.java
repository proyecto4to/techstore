package com.techstore.integracion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Los comprobantes con los que el cliente respalda el pago de su pedido.
 *
 * <p>El tope de tres se valida en el servidor a propósito: la pantalla puede
 * deshabilitar el botón al llegar al tercero, pero eso no impide mandar un
 * cuarto por la API. Lo mismo con el formato, que se comprueba mirando los
 * primeros bytes y no el encabezado que declara quien sube.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Comprobantes de pago")
class ComprobantesDePagoIT {

    /** Los primeros bytes de un JPEG de verdad. */
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10};

    @DynamicPropertySource
    static void base(DynamicPropertyRegistry registry) {
        BaseDatosDePrueba.registrar(registry);
    }

    @Autowired MockMvc mvc;
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void seAdjuntanHastaTresYElCuartoSeRechaza() throws Exception {
        String cliente = nuevoCliente("comprobantes.tope@techstore.py");
        long pedido = nuevoPedido(cliente);

        for (int i = 1; i <= 3; i++) {
            mvc.perform(multipart("/api/v1/pedidos/{id}/comprobantes", pedido)
                            .file(new MockMultipartFile("archivo", "boleta.jpg", "image/jpeg", JPEG))
                            .header("Authorization", bearer(cliente)))
                    .andExpect(status().isCreated());
        }

        // El cuarto no entra aunque se pida directo a la API.
        mvc.perform(multipart("/api/v1/pedidos/{id}/comprobantes", pedido)
                        .file(new MockMultipartFile("archivo", "boleta.jpg", "image/jpeg", JPEG))
                        .header("Authorization", bearer(cliente)))
                .andExpect(status().isConflict());

        assertEquals(3, comprobantes(cliente, pedido).size());
    }

    @Test
    void quitarUnoLibereLugarParaOtro() throws Exception {
        String cliente = nuevoCliente("comprobantes.quitar@techstore.py");
        long pedido = nuevoPedido(cliente);
        for (int i = 1; i <= 3; i++) {
            adjuntar(cliente, pedido);
        }

        long primero = comprobantes(cliente, pedido).get(0).path("id").asLong();
        mvc.perform(delete("/api/v1/pedidos/{id}/comprobantes/{c}", pedido, primero)
                        .header("Authorization", bearer(cliente)))
                .andExpect(status().isNoContent());

        assertEquals(2, comprobantes(cliente, pedido).size());
        // Y ahora sí entra otro.
        adjuntar(cliente, pedido);
        assertEquals(3, comprobantes(cliente, pedido).size());
    }

    @Test
    void unArchivoQueMienteSobreSuTipoNoEntra() throws Exception {
        String cliente = nuevoCliente("comprobantes.falso@techstore.py");
        long pedido = nuevoPedido(cliente);

        // Dice ser una imagen, pero los primeros bytes son texto.
        mvc.perform(multipart("/api/v1/pedidos/{id}/comprobantes", pedido)
                        .file(new MockMultipartFile(
                                "archivo", "trampa.jpg", "image/jpeg", "no soy una imagen".getBytes()))
                        .header("Authorization", bearer(cliente)))
                .andExpect(status().isConflict());

        assertTrue(comprobantes(cliente, pedido).isEmpty());
    }

    @Test
    void nadieVeLosComprobantesDeOtro() throws Exception {
        String duenio = nuevoCliente("comprobantes.duenio@techstore.py");
        long pedido = nuevoPedido(duenio);
        adjuntar(duenio, pedido);

        String ajeno = nuevoCliente("comprobantes.ajeno@techstore.py");
        mvc.perform(get("/api/v1/pedidos/{id}/comprobantes", pedido)
                        .header("Authorization", bearer(ajeno)))
                .andExpect(status().isForbidden());
    }

    @Test
    void laTiendaSiVeLosDelCliente() throws Exception {
        String cliente = nuevoCliente("comprobantes.tienda@techstore.py");
        long pedido = nuevoPedido(cliente);
        adjuntar(cliente, pedido);

        // Alguien tiene que poder mirar el respaldo para confirmar el pago.
        String admin = login("admin@techstore.py", "admin123");
        MvcResult resultado = mvc.perform(get("/api/v1/pedidos/{id}/comprobantes", pedido)
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andReturn();
        assertEquals(1, json.readTree(resultado.getResponse().getContentAsString()).size());
    }

    private void adjuntar(String token, long pedido) throws Exception {
        mvc.perform(multipart("/api/v1/pedidos/{id}/comprobantes", pedido)
                        .file(new MockMultipartFile("archivo", "boleta.jpg", "image/jpeg", JPEG))
                        .header("Authorization", bearer(token)))
                .andExpect(status().isCreated());
    }

    private java.util.List<JsonNode> comprobantes(String token, long pedido) throws Exception {
        MvcResult resultado = mvc.perform(get("/api/v1/pedidos/{id}/comprobantes", pedido)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode lista = json.readTree(resultado.getResponse().getContentAsString());
        java.util.List<JsonNode> items = new java.util.ArrayList<>();
        lista.forEach(items::add);
        return items;
    }

    /** Un pedido de retiro en tienda, que es el camino más corto a tener uno. */
    private long nuevoPedido(String token) throws Exception {
        MvcResult catalogo = mvc.perform(get("/api/v1/productos").param("size", "1"))
                .andExpect(status().isOk())
                .andReturn();
        long producto = json.readTree(catalogo.getResponse().getContentAsString())
                .path("content").get(0).path("id").asLong();

        MvcResult creado = mvc.perform(post("/api/v1/pedidos")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[{"productoId":%d,"cantidad":1}],
                                 "metodoEntrega":"RETIRO_TIENDA",
                                 "metodoPago":"TRANSFERENCIA_BANCARIA"}
                                """.formatted(producto)))
                .andExpect(status().isCreated())
                .andReturn();
        return json.readTree(creado.getResponse().getContentAsString()).path("id").asLong();
    }

    private String nuevoCliente(String email) throws Exception {
        mvc.perform(post("/api/v1/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Comprobante\",\"apellido\":\"Cliente\",\"email\":\"" + email
                                + "\",\"password\":\"Cliente123\"}"))
                .andExpect(status().isCreated());
        return login(email, "Cliente123");
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
