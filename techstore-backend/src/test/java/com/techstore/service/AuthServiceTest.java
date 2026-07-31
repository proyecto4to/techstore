package com.techstore.service;

import com.techstore.dto.request.CambioPasswordRequest;
import com.techstore.dto.request.LoginRequest;
import com.techstore.dto.request.RefreshTokenRequest;
import com.techstore.dto.request.RegistroRequest;
import com.techstore.dto.response.AuthResponse;
import com.techstore.entity.IntentoLogin;
import com.techstore.entity.Rol;
import com.techstore.entity.Usuario;
import com.techstore.exception.BusinessException;
import com.techstore.exception.CuentaInactivaException;
import com.techstore.exception.TooManyAttemptsException;
import com.techstore.repository.IntentoLoginRepository;
import com.techstore.repository.RolRepository;
import com.techstore.repository.UsuarioRepository;
import com.techstore.config.EmpresaProperties;
import com.techstore.security.JwtService;
import com.techstore.security.LoginRateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private RolRepository rolRepository;

    @Mock
    private CorreoService correoService;

    @Mock
    private IntentoLoginRepository intentoLoginRepository;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final JwtService jwtService =
            new JwtService("clave-de-prueba-suficientemente-larga-para-hmac-sha!!", 900_000, 604_800_000);

    private AuthService authService;
    private Usuario admin;

    @BeforeEach
    void setUp() {
        EmpresaProperties empresa = new EmpresaProperties(
                "TechStore Paraguay", null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null);
        EmailTemplateService emailTemplateService = new EmailTemplateService(empresa, "http://localhost:5173");

        // El limitador ahora guarda los intentos en la base. Se le da un
        // repositorio de mentira sobre un Map para que el test siga probando la
        // regla (cinco fallos bloquean) y no la persistencia.
        Map<String, IntentoLogin> intentos = new HashMap<>();
        lenient().when(intentoLoginRepository.findById(anyString()))
                .thenAnswer(inv -> Optional.ofNullable(intentos.get(inv.<String>getArgument(0))));
        lenient().when(intentoLoginRepository.save(any(IntentoLogin.class)))
                .thenAnswer(inv -> {
                    IntentoLogin intento = inv.getArgument(0);
                    intentos.put(intento.getEmail(), intento);
                    return intento;
                });
        lenient().doAnswer(inv -> intentos.remove(inv.<String>getArgument(0)))
                .when(intentoLoginRepository).deleteById(anyString());

        authService = new AuthService(
                usuarioRepository, rolRepository, encoder, jwtService, correoService,
                emailTemplateService, new LoginRateLimiter(intentoLoginRepository));

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

        // Con la contraseña correcta pero cuenta inactiva se informa el bloqueo (no "credenciales").
        assertThrows(CuentaInactivaException.class,
                () -> authService.login(new LoginRequest("admin@techstore.py", "admin123")));
    }

    @Test
    void loginDeUsuarioInactivoConPasswordIncorrectaSigueSiendoCredenciales() {
        admin.setActivo(false);
        when(usuarioRepository.findByEmail("admin@techstore.py")).thenReturn(Optional.of(admin));

        // Si además la contraseña es incorrecta, no se revela el estado de la cuenta.
        assertThrows(BadCredentialsException.class,
                () -> authService.login(new LoginRequest("admin@techstore.py", "incorrecta")));
    }

    @Test
    void loginBloqueaTrasVariosIntentosFallidos() {
        when(usuarioRepository.findByEmail("admin@techstore.py")).thenReturn(Optional.of(admin));

        for (int i = 0; i < 5; i++) {
            assertThrows(BadCredentialsException.class,
                    () -> authService.login(new LoginRequest("admin@techstore.py", "incorrecta")));
        }

        // Al sexto intento se bloquea aunque la contraseña ahora sea correcta.
        assertThrows(TooManyAttemptsException.class,
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

    @Test
    void registroCreaClienteConSesionIniciada() {
        Rol rolCliente = new Rol();
        rolCliente.setId(3L);
        rolCliente.setNombre("Cliente");
        when(usuarioRepository.existsByEmail("juan@test.py")).thenReturn(false);
        when(rolRepository.findByNombre("Cliente")).thenReturn(Optional.of(rolCliente));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> {
            Usuario u = inv.getArgument(0);
            u.setId(20L);
            return u;
        });

        AuthResponse response = authService.registrar(
                new RegistroRequest("Juan", "Pérez", "juan@test.py", "clave12345"));

        assertNotNull(response.accessToken());
        assertEquals("juan@test.py", response.usuario().email());
        assertEquals("Cliente", response.usuario().rol());
    }

    @Test
    void registroConEmailDuplicadoFalla() {
        when(usuarioRepository.existsByEmail("admin@techstore.py")).thenReturn(true);

        assertThrows(BusinessException.class, () -> authService.registrar(
                new RegistroRequest("Otro", "Admin", "admin@techstore.py", "clave12345")));
    }

    @Test
    void cambiarPasswordConActualCorrecta() {
        when(usuarioRepository.findByEmail("admin@techstore.py")).thenReturn(Optional.of(admin));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        authService.cambiarPassword("admin@techstore.py",
                new CambioPasswordRequest("admin123", "nuevaClave99"));

        org.junit.jupiter.api.Assertions.assertTrue(
                encoder.matches("nuevaClave99", admin.getPasswordHash()));
    }

    @Test
    void cambiarPasswordConActualIncorrectaFalla() {
        when(usuarioRepository.findByEmail("admin@techstore.py")).thenReturn(Optional.of(admin));

        assertThrows(BadCredentialsException.class, () -> authService.cambiarPassword(
                "admin@techstore.py", new CambioPasswordRequest("equivocada", "nuevaClave99")));
    }

    @Test
    void registroGuardaPasswordHasheada() {
        Rol rolCliente = new Rol();
        rolCliente.setId(3L);
        rolCliente.setNombre("Cliente");
        when(usuarioRepository.existsByEmail("ana@test.py")).thenReturn(false);
        when(rolRepository.findByNombre("Cliente")).thenReturn(Optional.of(rolCliente));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> {
            Usuario u = inv.getArgument(0);
            u.setId(21L);
            return u;
        });

        authService.registrar(new RegistroRequest("Ana", "López", "ana@test.py", "secreta123"));

        org.mockito.ArgumentCaptor<Usuario> captor = org.mockito.ArgumentCaptor.forClass(Usuario.class);
        org.mockito.Mockito.verify(usuarioRepository).save(captor.capture());
        String hash = captor.getValue().getPasswordHash();
        org.junit.jupiter.api.Assertions.assertNotEquals("secreta123", hash);
        org.junit.jupiter.api.Assertions.assertTrue(encoder.matches("secreta123", hash));
    }
}
