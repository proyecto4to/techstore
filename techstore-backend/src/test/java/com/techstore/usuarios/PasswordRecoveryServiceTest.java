package com.techstore.usuarios;

import com.techstore.comun.BusinessException;
import com.techstore.notificaciones.CorreoService;
import com.techstore.notificaciones.EmailTemplateService;
import com.techstore.notificaciones.EstadoCorreo;
import com.techstore.notificaciones.TipoCorreo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordRecoveryServiceTest {

    @Mock PasswordResetTokenRepository tokenRepository;
    @Mock UsuarioRepository usuarioRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock RefreshSessionService refreshSessionService;
    @Mock CorreoService correoService;
    @Mock EmailTemplateService emailTemplateService;

    PasswordRecoveryService service;
    Usuario usuario;

    @BeforeEach
    void setUp() {
        service = new PasswordRecoveryService(
                tokenRepository, usuarioRepository, passwordEncoder,
                refreshSessionService, correoService, emailTemplateService);
        ReflectionTestUtils.setField(service, "passwordResetUrl", "techstore://app/password/reset");
        ReflectionTestUtils.setField(service, "expirationMinutes", 15L);

        usuario = new Usuario();
        usuario.setId(8L);
        usuario.setTenantId(1L);
        usuario.setEmail("cliente@techstore.py");
        usuario.setNombre("Cliente");
        usuario.setApellido("Prueba");
        usuario.setActivo(true);
    }

    @Test
    void correoInexistenteNoRevelaLaCuentaNiGeneraToken() {
        when(usuarioRepository.findByEmail("nadie@techstore.py")).thenReturn(Optional.empty());

        service.solicitar("nadie@techstore.py");

        verify(tokenRepository, never()).save(any());
        verify(correoService, never()).enviar(any(), anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void generaTokenAleatorioYGuardaSoloSuHash() {
        when(usuarioRepository.findByEmail(usuario.getEmail())).thenReturn(Optional.of(usuario));
        when(tokenRepository.existsByUsuarioIdAndUsadoEnIsNullAndExpiraEnAfter(any(), any())).thenReturn(false);
        when(emailTemplateService.construir(any(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString())).thenAnswer(inv -> inv.getArgument(6));
        when(correoService.enviar(any(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(EstadoCorreo.SIMULADO);

        service.solicitar(usuario.getEmail());

        ArgumentCaptor<PasswordResetToken> entityCaptor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokenRepository).save(entityCaptor.capture());
        PasswordResetToken entity = entityCaptor.getValue();
        assertEquals(64, entity.getTokenHash().length());
        assertEquals(1L, entity.getTenantId());

        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(correoService).enviar(
                any(TipoCorreo.class), anyString(), anyString(), anyString(), bodyCaptor.capture(), anyString());
        String rawToken = bodyCaptor.getValue().substring(bodyCaptor.getValue().indexOf("token=") + 6);
        assertNotEquals(rawToken, entity.getTokenHash());
        assertEquals(hash(rawToken), entity.getTokenHash());
    }

    @Test
    void tokenSeUsaUnaVezCambiaPasswordYRevocaSesiones() {
        String rawToken = "token-aleatorio-de-prueba";
        PasswordResetToken entity = new PasswordResetToken();
        entity.setUsuario(usuario);
        entity.setTokenHash(hash(rawToken));
        entity.setExpiraEn(java.time.LocalDateTime.now(java.time.ZoneOffset.UTC).plusMinutes(5));
        when(tokenRepository.buscarParaActualizar(hash(rawToken))).thenReturn(Optional.of(entity));
        when(passwordEncoder.encode("NuevaClave1")).thenReturn("encoded");

        service.completar(new CompletarRecuperacionRequest(rawToken, "NuevaClave1"));

        assertEquals("encoded", usuario.getPasswordHash());
        assertTrue(entity.getUsadoEn() != null);
        verify(refreshSessionService).revocarTodas(usuario);
    }

    @Test
    void tokenDesconocidoEsRechazado() {
        when(tokenRepository.buscarParaActualizar(anyString())).thenReturn(Optional.empty());

        assertThrows(BusinessException.class,
                () -> service.completar(new CompletarRecuperacionRequest("invalido", "NuevaClave1")));
    }

    @Test
    void tokenVencidoEsRechazadoSinCambiarPassword() {
        String rawToken = "token-vencido-de-prueba";
        PasswordResetToken entity = new PasswordResetToken();
        entity.setUsuario(usuario);
        entity.setTokenHash(hash(rawToken));
        entity.setExpiraEn(java.time.LocalDateTime.now(java.time.ZoneOffset.UTC).minusSeconds(1));
        when(tokenRepository.buscarParaActualizar(hash(rawToken))).thenReturn(Optional.of(entity));

        assertThrows(BusinessException.class,
                () -> service.completar(new CompletarRecuperacionRequest(rawToken, "NuevaClave1")));

        verify(passwordEncoder, never()).encode(anyString());
        verify(refreshSessionService, never()).revocarTodas(any());
    }

    private static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
