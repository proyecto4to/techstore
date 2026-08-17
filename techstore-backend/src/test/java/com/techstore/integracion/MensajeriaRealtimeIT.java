package com.techstore.integracion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.techstore.mensajeria.EnviarMensajeRequest;
import com.techstore.mensajeria.LecturaResponse;
import com.techstore.mensajeria.MensajeResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DisplayName("Mensajería REST y STOMP en tiempo real")
class MensajeriaRealtimeIT {
    @DynamicPropertySource
    static void base(DynamicPropertyRegistry registro) {
        BaseDatosDePrueba.registrar(registro);
    }

    @LocalServerPort private int port;
    @Autowired private TestRestTemplate rest;
    @Autowired private ObjectMapper json;

    @Test
    @DisplayName("cliente y Admin intercambian mensajes y lectura con identidad JWT")
    void flujoBidireccional() throws Exception {
        String clienteToken = registrarCliente();
        String adminToken = login("admin@techstore.py", "admin123");
        long conversacionId = crearConversacion(clienteToken);
        WebSocketStompClient stomp = stompClient();
        StompSession cliente = conectar(stomp, clienteToken);
        StompSession admin = conectar(stomp, adminToken);

        try {
            AtomicReference<MensajeResponse> recibidoPorAdmin = new AtomicReference<>();
            CountDownLatch adminLatch = new CountDownLatch(1);
            admin.subscribe("/user/queue/chat", frame(MensajeResponse.class, recibidoPorAdmin, adminLatch));

            cliente.send("/app/chat/" + conversacionId,
                    new EnviarMensajeRequest("it-cliente-1", "Mensaje desde Android"));

            assertTrue(adminLatch.await(10, TimeUnit.SECONDS));
            assertEquals("Mensaje desde Android", recibidoPorAdmin.get().contenido());
            assertEquals("cliente.chat@techstore.py", recibidoPorAdmin.get().remitenteEmail());

            AtomicReference<MensajeResponse> recibidoPorCliente = new AtomicReference<>();
            CountDownLatch clienteLatch = new CountDownLatch(1);
            cliente.subscribe("/user/queue/chat", frame(MensajeResponse.class, recibidoPorCliente, clienteLatch));
            admin.send("/app/chat/" + conversacionId,
                    new EnviarMensajeRequest("it-admin-1", "Respuesta desde Admin Web"));

            assertTrue(clienteLatch.await(10, TimeUnit.SECONDS));
            assertEquals("Respuesta desde Admin Web", recibidoPorCliente.get().contenido());
            assertEquals("admin@techstore.py", recibidoPorCliente.get().remitenteEmail());

            AtomicReference<LecturaResponse> lecturaAdmin = new AtomicReference<>();
            CountDownLatch lecturaLatch = new CountDownLatch(1);
            admin.subscribe("/user/queue/chat-read", frame(LecturaResponse.class, lecturaAdmin, lecturaLatch));
            ResponseEntity<String> lectura = exchange("/api/v1/conversaciones/" + conversacionId + "/leido",
                    HttpMethod.PUT, clienteToken, null);

            assertTrue(lectura.getStatusCode().is2xxSuccessful());
            assertTrue(lecturaLatch.await(10, TimeUnit.SECONDS));
            assertEquals(conversacionId, lecturaAdmin.get().conversacionId());
            assertTrue(lecturaAdmin.get().mensajes() >= 1);
        } finally {
            if (cliente.isConnected()) cliente.disconnect();
            if (admin.isConnected()) admin.disconnect();
            stomp.stop();
        }
    }

    private String registrarCliente() throws Exception {
        ResponseEntity<String> response = rest.postForEntity("/api/v1/auth/registro", Map.of(
                "nombre", "Cliente", "apellido", "Chat", "email", "cliente.chat@techstore.py",
                "password", "Cliente123"), String.class);
        assertTrue(response.getStatusCode().is2xxSuccessful());
        return json.readTree(response.getBody()).path("accessToken").asText();
    }

    private String login(String email, String password) throws Exception {
        ResponseEntity<String> response = rest.postForEntity("/api/v1/auth/login",
                Map.of("email", email, "password", password), String.class);
        assertTrue(response.getStatusCode().is2xxSuccessful());
        return json.readTree(response.getBody()).path("accessToken").asText();
    }

    private long crearConversacion(String token) throws Exception {
        ResponseEntity<String> response = exchange("/api/v1/conversaciones", HttpMethod.POST, token,
                Map.of("clientMessageId", "it-inicial-1", "mensaje", "Consulta inicial"));
        assertTrue(response.getStatusCode().is2xxSuccessful());
        JsonNode body = json.readTree(response.getBody());
        return body.path("conversacion").path("id").asLong();
    }

    private ResponseEntity<String> exchange(String path, HttpMethod method, String token, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return rest.exchange(path, method, new HttpEntity<>(body, headers), String.class);
    }

    private WebSocketStompClient stompClient() {
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        converter.setObjectMapper(json);
        client.setMessageConverter(converter);
        return client;
    }

    private StompSession conectar(WebSocketStompClient client, String token) throws Exception {
        StompHeaders headers = new StompHeaders();
        headers.add("Authorization", "Bearer " + token);
        StompSession session = client.connectAsync("ws://localhost:" + port + "/ws",
                new WebSocketHttpHeaders(), headers, new StompSessionHandlerAdapter() { }).get(10, TimeUnit.SECONDS);
        assertNotNull(session);
        assertTrue(session.isConnected());
        return session;
    }

    private <T> StompFrameHandler frame(Class<T> type, AtomicReference<T> target, CountDownLatch latch) {
        return new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return type;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                target.set(type.cast(payload));
                latch.countDown();
            }
        };
    }
}
