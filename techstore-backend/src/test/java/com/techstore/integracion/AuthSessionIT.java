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

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Sesiones refresh seguras")
class AuthSessionIT {

    @DynamicPropertySource
    static void base(DynamicPropertyRegistry registro) {
        BaseDatosDePrueba.registrar(registro);
    }

    @Autowired
    private MockMvc mvc;

    private final ObjectMapper json = new ObjectMapper();

    @Test
    @DisplayName("rota cada refresh, detecta reutilización y permite cerrar sesión")
    void cicloCompleto() throws Exception {
        String inicial = login();
        String rotado = refresh(inicial);

        assertNotNull(rotado);
        assertNotEquals(inicial, rotado);

        mvc.perform(post("/api/v1/auth/refresh")
                        .header("Authorization", "Bearer " + inicial)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(inicial)))
                .andExpect(status().isUnauthorized());

        // La reutilización revoca toda la familia. Un nuevo login crea una
        // sesión independiente que después debe poder cerrarse explícitamente.
        String paraLogout = login();
        mvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + paraLogout)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(paraLogout)))
                .andExpect(status().isNoContent());

        mvc.perform(post("/api/v1/auth/refresh")
                        .header("Authorization", "Bearer " + paraLogout)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(paraLogout)))
                .andExpect(status().isUnauthorized());
    }

    private String login() throws Exception {
        MvcResult resultado = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"admin@techstore.py","password":"admin123"}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        return json.readTree(resultado.getResponse().getContentAsString()).path("refreshToken").asText();
    }

    private String refresh(String token) throws Exception {
        MvcResult resultado = mvc.perform(post("/api/v1/auth/refresh")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(token)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode respuesta = json.readTree(resultado.getResponse().getContentAsString());
        return respuesta.path("refreshToken").asText();
    }

    private String cuerpo(String token) throws Exception {
        return json.writeValueAsString(json.createObjectNode().put("refreshToken", token));
    }
}
