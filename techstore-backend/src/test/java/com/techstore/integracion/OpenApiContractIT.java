package com.techstore.integracion;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenApiContractIT {
    @DynamicPropertySource
    static void base(DynamicPropertyRegistry registry) {
        BaseDatosDePrueba.registrar(registry);
    }

    @Autowired MockMvc mvc;

    @Test
    void exportaContratoDesdeLaImplementacionCompilada() throws Exception {
        String json = mvc.perform(get("/api/v1/api-docs"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Files.writeString(Path.of("target", "openapi-current.json"), json, StandardCharsets.UTF_8);
    }
}
