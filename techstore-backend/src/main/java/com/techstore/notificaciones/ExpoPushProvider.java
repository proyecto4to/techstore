package com.techstore.notificaciones;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@ConditionalOnProperty(prefix = "push", name = "provider", havingValue = "expo")
public class ExpoPushProvider implements PushProvider {

    private final ObjectMapper objectMapper;
    private final PushProperties properties;
    private final HttpClient httpClient;

    public ExpoPushProvider(ObjectMapper objectMapper, PushProperties properties) {
        this(objectMapper, properties, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build());
    }

    ExpoPushProvider(ObjectMapper objectMapper, PushProperties properties, HttpClient httpClient) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.httpClient = httpClient;
    }

    @Override
    public PushResult enviar(PushMessage mensaje) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("to", mensaje.token());
            payload.put("title", mensaje.titulo());
            payload.put("body", mensaje.cuerpo());
            payload.put("sound", "default");
            payload.put("channelId", "techstore-default");
            Map<String, Object> data = new LinkedHashMap<>(mensaje.datos());
            data.put("url", mensaje.deepLink());
            payload.put("data", data);

            HttpRequest.Builder request = HttpRequest.newBuilder(properties.expoUrl())
                    .timeout(Duration.ofSeconds(10))
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)));
            if (properties.accessToken() != null && !properties.accessToken().isBlank()) {
                request.header("Authorization", "Bearer " + properties.accessToken().trim());
            }
            HttpResponse<String> response = httpClient.send(
                    request.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return PushResult.error("EXPO_HTTP_" + response.statusCode(), false);
            }
            return interpretar(objectMapper.readTree(response.body()));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return PushResult.error("EXPO_INTERRUPTED", false);
        } catch (IOException | RuntimeException exception) {
            return PushResult.error("EXPO_UNAVAILABLE", false);
        }
    }

    private PushResult interpretar(JsonNode root) {
        JsonNode data = root.path("data");
        if (data.isArray()) data = data.path(0);
        String status = data.path("status").asText();
        if ("ok".equals(status)) {
            return PushResult.aceptado(data.path("id").asText(null));
        }
        String codigo = data.path("details").path("error").asText("EXPO_REJECTED");
        return PushResult.error(codigo, "DeviceNotRegistered".equals(codigo));
    }
}
