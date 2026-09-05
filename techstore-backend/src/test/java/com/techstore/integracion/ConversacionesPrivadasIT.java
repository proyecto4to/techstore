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

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Las conversaciones privadas entre personas, y quién puede escribirle a quién.
 *
 * <p>Lo que se comprueba acá es que la regla vive en el servidor. Esconder un
 * usuario de la lista no es suficiente: si alguien manda el id igual, tiene que
 * recibir un rechazo, y si pide la lista de un rol que no le corresponde tiene
 * que recibirla vacía en lugar de la lista real.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Conversaciones privadas")
class ConversacionesPrivadasIT {

    @DynamicPropertySource
    static void base(DynamicPropertyRegistry registry) {
        BaseDatosDePrueba.registrar(registry);
    }

    @Autowired MockMvc mvc;
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void elCajeroSoloVeAdministradoresParaEscribirles() throws Exception {
        String cajero = nuevoUsuario("privadas.cajero@techstore.py", "Cajero");

        JsonNode lista = destinatarios(cajero, null, null);
        assertFalse(lista.isEmpty(), "El cajero tiene que poder escribirle a administración");
        for (JsonNode candidato : lista) {
            assertEquals("Admin", candidato.path("rol").asText(),
                    "Al cajero le apareció alguien que no es administrador");
        }
    }

    @Test
    void pedirUnRolQueNoCorrespondeDevuelveVacioYNoLaListaReal() throws Exception {
        String cajero = nuevoUsuario("privadas.filtro@techstore.py", "Cajero");
        // Que haya clientes para encontrar, si el filtro no se respetara.
        nuevoCliente("privadas.victima@techstore.py");

        assertEquals(0, destinatarios(cajero, null, "Cliente").size(),
                "El filtro por rol amplió lo que el rol del cajero permite");
    }

    @Test
    void elClienteNoTienePantallaDeDestinatarios() throws Exception {
        String cliente = nuevoCliente("privadas.cliente@techstore.py");

        // El cliente no elige con quién habla: tiene «Atención al Cliente».
        mvc.perform(get("/api/v1/conversaciones/destinatarios")
                        .header("Authorization", bearer(cliente)))
                .andExpect(status().isConflict());
    }

    @Test
    void mandarElIdDeUnClienteNoAbreLaConversacion() throws Exception {
        String admin = login("admin@techstore.py", "admin123");
        String cajero = nuevoUsuario("privadas.intento@techstore.py", "Cajero");
        String cliente = nuevoCliente("privadas.objetivo@techstore.py");
        long clienteId = miId(cliente);

        // Esconder al cliente de la lista no alcanza: el id se puede mandar igual.
        mvc.perform(post("/api/v1/conversaciones")
                        .header("Authorization", bearer(cajero))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(clienteId, "Hola")))
                .andExpect(status().isForbidden());

        // Administración sí puede, para que quede claro que el rechazo es por rol.
        mvc.perform(post("/api/v1/conversaciones")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(clienteId, "Hola, te escribo de la tienda")))
                .andExpect(status().isCreated());
    }

    @Test
    void escribirDosVecesALaMismaPersonaNoDuplicaLaConversacion() throws Exception {
        String admin = login("admin@techstore.py", "admin123");
        String repartidor = nuevoUsuario("privadas.repartidor@techstore.py", "Repartidor");
        long repartidorId = miId(repartidor);

        long primera = crearPrivada(admin, repartidorId, "Primera");
        long segunda = crearPrivada(admin, repartidorId, "Segunda");

        assertEquals(primera, segunda, "Se abrió una conversación nueva en lugar de retomar la que había");

        // Y da igual quién la abra: el repartidor escribe al mismo hilo.
        long desdeElOtroLado = crearPrivada(repartidor, miId(admin), "Respondo");
        assertEquals(primera, desdeElOtroLado,
                "El mismo par de personas terminó con dos conversaciones");
    }

    @Test
    void nadieMasLeeUnaConversacionPrivada() throws Exception {
        String admin = login("admin@techstore.py", "admin123");
        String repartidor = nuevoUsuario("privadas.reservada@techstore.py", "Repartidor");
        long conversacion = crearPrivada(admin, miId(repartidor), "Tema entre nosotros");

        // Un cajero es del mostrador y ve todo el soporte, pero no esto.
        String cajero = nuevoUsuario("privadas.curioso@techstore.py", "Cajero");
        mvc.perform(get("/api/v1/conversaciones/{id}", conversacion)
                        .header("Authorization", bearer(cajero)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/conversaciones/{id}/mensajes", conversacion)
                        .header("Authorization", bearer(cajero)))
                .andExpect(status().isForbidden());
    }

    @Test
    void elClienteVeAtencionAlClienteYNoElNombreDeQuienLeResponde() throws Exception {
        String cliente = nuevoCliente("privadas.soporte@techstore.py");
        mvc.perform(post("/api/v1/conversaciones")
                        .header("Authorization", bearer(cliente))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(null, "Necesito ayuda con mi pedido")))
                .andExpect(status().isCreated());

        MvcResult resultado = mvc.perform(get("/api/v1/conversaciones")
                        .header("Authorization", bearer(cliente)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode primera = json.readTree(resultado.getResponse().getContentAsString())
                .path("content").get(0);
        assertEquals("Atención al Cliente", primera.path("titulo").asText());
        // El rol y el nombre de quien atiende no viajan: si viajaran, bastaría
        // mirar la respuesta de la API para saltear la abstracción.
        assertTrue(primera.path("rolContraparte").isNull(),
                "Al cliente le llegó el rol de quien lo atiende");
        assertTrue(primera.path("contraparteEmail").isNull(),
                "Al cliente le llegó el correo de quien lo atiende");
    }

    @Test
    void elMostradorVeElSoporteConElNombreDelCliente() throws Exception {
        String cliente = nuevoCliente("privadas.consulta@techstore.py");
        mvc.perform(post("/api/v1/conversaciones")
                        .header("Authorization", bearer(cliente))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(null, "Una consulta")))
                .andExpect(status().isCreated());

        String admin = login("admin@techstore.py", "admin123");
        MvcResult resultado = mvc.perform(get("/api/v1/conversaciones").param("size", "50")
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode contenido = json.readTree(resultado.getResponse().getContentAsString()).path("content");
        boolean encontrada = false;
        for (JsonNode conversacion : contenido) {
            if ("SOPORTE".equals(conversacion.path("tipo").asText())
                    && conversacion.path("titulo").asText().contains("Soporte")) {
                encontrada = true;
            }
        }
        assertTrue(encontrada, "El mostrador no vio la consulta con el nombre de quien la hizo");
    }

    // ------------------------------------------------------------------

    private long crearPrivada(String token, long destinatarioId, String mensaje) throws Exception {
        MvcResult resultado = mvc.perform(post("/api/v1/conversaciones")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(destinatarioId, mensaje)))
                .andExpect(status().isCreated())
                .andReturn();
        return json.readTree(resultado.getResponse().getContentAsString())
                .path("conversacion").path("id").asLong();
    }

    private String cuerpo(Long destinatarioId, String mensaje) {
        String destino = destinatarioId == null ? "null" : destinatarioId.toString();
        return "{\"destinatarioId\":" + destino
                + ",\"clientMessageId\":\"" + UUID.randomUUID()
                + "\",\"mensaje\":\"" + mensaje + "\"}";
    }

    private JsonNode destinatarios(String token, String q, String rol) throws Exception {
        var peticion = get("/api/v1/conversaciones/destinatarios").header("Authorization", bearer(token));
        if (q != null) peticion = peticion.param("q", q);
        if (rol != null) peticion = peticion.param("rol", rol);
        MvcResult resultado = mvc.perform(peticion).andExpect(status().isOk()).andReturn();
        return json.readTree(resultado.getResponse().getContentAsString());
    }

    private long miId(String token) throws Exception {
        MvcResult resultado = mvc.perform(get("/api/v1/auth/me").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn();
        return json.readTree(resultado.getResponse().getContentAsString()).path("id").asLong();
    }

    private String nuevoCliente(String email) throws Exception {
        mvc.perform(post("/api/v1/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Soporte\",\"apellido\":\"Cliente\",\"email\":\"" + email
                                + "\",\"password\":\"Cliente123\"}"))
                .andExpect(status().isCreated());
        return login(email, "Cliente123");
    }

    private String nuevoUsuario(String email, String rol) throws Exception {
        String admin = login("admin@techstore.py", "admin123");
        mvc.perform(post("/api/v1/usuarios")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"" + rol + "\",\"apellido\":\"Privadas\",\"email\":\"" + email
                                + "\",\"password\":\"Interno123\",\"rol\":\"" + rol + "\"}"))
                .andExpect(status().is2xxSuccessful());
        return login(email, "Interno123");
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
