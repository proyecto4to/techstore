package com.techstore.integracion;

import com.techstore.comun.tenant.TenantContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PasswordRecoveryIT {

    private static final Pattern TOKEN = Pattern.compile("token=([A-Za-z0-9_-]+)");

    @DynamicPropertySource
    static void base(DynamicPropertyRegistry registry) {
        BaseDatosDePrueba.registrar(registry);
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @Test
    void recuperacionEsOpacaDeUnSoloUsoYPermiteNuevoLogin() throws Exception {
        String email = "recovery@techstore.py";
        mvc.perform(post("/api/v1/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Recovery","apellido":"Test","email":"%s","password":"Inicial123"}
                                """.formatted(email)))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/v1/auth/password/forgot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isAccepted());
        mvc.perform(post("/api/v1/auth/password/forgot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"inexistente@techstore.py\"}"))
                .andExpect(status().isAccepted());

        String body = TenantContext.comoTenant(1L, () -> jdbc.queryForObject(
                "SELECT cuerpo FROM correos WHERE destinatario=? AND tipo='RESETEO_PASSWORD' ORDER BY id DESC LIMIT 1",
                String.class, email));
        // El enlace tiene que abrirse en cualquier navegador. Antes apuntaba a
        // `techstore://`, un esquema propio que solo resuelve si la app esta
        // instalada: quien pedia recuperar la clave desde la computadora
        // recibia un enlace muerto.
        assertTrue(body.contains("/password/reset?token="),
                "El correo debe llevar a la pantalla de nueva contrasena");
        assertFalse(body.contains("techstore://"),
                "El enlace de recuperacion no puede depender de un esquema propio");

        Matcher matcher = TOKEN.matcher(body);
        assertTrue(matcher.find());
        String token = matcher.group(1);
        assertFalse(token.isBlank());

        mvc.perform(post("/api/v1/auth/password/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\",\"password\":\"NuevaClave1\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/auth/password/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\",\"password\":\"OtraClave1\"}"))
                .andExpect(status().isConflict());

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"NuevaClave1\"}"))
                .andExpect(status().isOk());
    }
}
