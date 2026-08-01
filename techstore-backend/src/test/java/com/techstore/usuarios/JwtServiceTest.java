package com.techstore.usuarios;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTest {

    private static final String SECRET = "clave-de-prueba-suficientemente-larga-para-hmac-sha!!";

    private final JwtService jwtService = new JwtService(SECRET, 900_000, 604_800_000);

    private Usuario usuarioDePrueba() {
        Rol rol = new Rol();
        rol.setId(1L);
        rol.setNombre("Admin");

        Usuario usuario = new Usuario();
        usuario.setId(7L);
        usuario.setEmail("admin@techstore.py");
        usuario.setNombre("Admin");
        usuario.setApellido("Sistema");
        usuario.setRol(rol);
        return usuario;
    }

    @Test
    void accessTokenRoundTripConservaSubjectYClaims() {
        String token = jwtService.generateAccessToken(usuarioDePrueba());

        Claims claims = jwtService.parseToken(token);

        assertEquals("admin@techstore.py", claims.getSubject());
        assertEquals("Admin", claims.get("rol", String.class));
        assertEquals(7L, claims.get("uid", Long.class));
        assertTrue(jwtService.isAccessToken(claims));
        assertFalse(jwtService.isRefreshToken(claims));
    }

    @Test
    void refreshTokenSeDistingueDelAccessToken() {
        String token = jwtService.generateRefreshToken(usuarioDePrueba());

        Claims claims = jwtService.parseToken(token);

        assertTrue(jwtService.isRefreshToken(claims));
        assertFalse(jwtService.isAccessToken(claims));
    }

    @Test
    void tokenExpiradoEsRechazado() {
        JwtService vencido = new JwtService(SECRET, -1_000, -1_000);
        String token = vencido.generateAccessToken(usuarioDePrueba());

        assertThrows(JwtException.class, () -> jwtService.parseToken(token));
    }

    @Test
    void tokenFirmadoConOtraClaveEsRechazado() {
        JwtService otraClave = new JwtService("otra-clave-distinta-tambien-larga-para-hmac-sha256!!", 900_000, 900_000);
        String token = otraClave.generateAccessToken(usuarioDePrueba());

        assertThrows(JwtException.class, () -> jwtService.parseToken(token));
    }
}
