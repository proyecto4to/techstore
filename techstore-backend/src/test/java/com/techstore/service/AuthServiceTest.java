package com.techstore.service;

import com.techstore.dto.request.LoginRequest;
import com.techstore.dto.request.RefreshTokenRequest;
import com.techstore.dto.response.AuthResponse;
import com.techstore.entity.Rol;
import com.techstore.entity.Usuario;
import com.techstore.repository.UsuarioRepository;
import com.techstore.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final JwtService jwtService =
            new JwtService("clave-de-prueba-suficientemente-larga-para-hmac-sha!!", 900_000, 604_800_000);

    private AuthService authService;
    private Usuario admin;

    @BeforeEach
    void setUp() {
        authService = new AuthService(usuarioRepository, encoder, jwtService);

        Rol rol = new Rol();
        rol.setId(1L);
        rol.setNombre("Admin");

        admin = new Usuario();
        admin.setId(1L);
        admin.setEmail("admin@techstore.py");
        admin.setNombre("Admin");
        admin.setApellido("Sistema");
        admin.setRol(rol);
        admin.setActivo(true);
        admin.setPasswordHash(encoder.encode("admin123"));
    }

    @Test
    void loginConCredencialesCorrectasDevuelveTokens() {
        when(usuarioRepository.findByEmail("admin@techstore.py")).thenReturn(Optional.of(admin));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        AuthResponse response = authService.login(new LoginRequest("admin@techstore.py", "admin123"));

        assertNotNull(response.accessToken());
        assertNotNull(response.refreshToken());
        assertEquals("Bearer", response.tokenType());
        assertEquals("admin@techstore.py", response.usuario().email());
        assertNotNull(admin.getLastLogin());
    }

    @Test
    void loginConPasswordIncorrectaFalla() {
        when(usuarioRepository.findByEmail("admin@techstore.py")).thenReturn(Optional.of(admin));

        assertThrows(BadCredentialsException.class,
                () -> authService.login(new LoginRequest("admin@techstore.py", "incorrecta")));
    }

    @Test
    void loginDeUsuarioInactivoFalla() {
        admin.setActivo(false);
        when(usuarioRepository.findByEmail("admin@techstore.py")).thenReturn(Optional.of(admin));

        assertThrows(BadCredentialsException.class,
                () -> authService.login(new LoginRequest("admin@techstore.py", "admin123")));
    }

    @Test
    void loginDeEmailInexistenteFalla() {
        when(usuarioRepository.findByEmail("nadie@techstore.py")).thenReturn(Optional.empty());

        assertThrows(BadCredentialsException.class,
                () -> authService.login(new LoginRequest("nadie@techstore.py", "admin123")));
    }

    @Test
    void refreshConRefreshTokenValidoEmiteNuevoPar() {
        when(usuarioRepository.findByEmail("admin@techstore.py")).thenReturn(Optional.of(admin));
        String refreshToken = jwtService.generateRefreshToken(admin);

        AuthResponse response = authService.refresh(new RefreshTokenRequest(refreshToken));

        assertNotNull(response.accessToken());
        assertEquals("admin@techstore.py", response.usuario().email());
    }

    @Test
    void refreshConAccessTokenEsRechazado() {
        String accessToken = jwtService.generateAccessToken(admin);

        assertThrows(BadCredentialsException.class,
                () -> authService.refresh(new RefreshTokenRequest(accessToken)));
    }
}
