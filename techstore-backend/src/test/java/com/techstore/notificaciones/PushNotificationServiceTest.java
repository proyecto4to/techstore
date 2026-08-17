package com.techstore.notificaciones;

import com.techstore.usuarios.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PushNotificationServiceTest {

    @Mock private DispositivoMovilRepository dispositivoRepository;
    @Mock private PushProvider provider;

    private PushNotificationService service;

    @BeforeEach
    void setUp() {
        service = new PushNotificationService(dispositivoRepository, provider);
    }

    @Test
    void noEnviaDosVecesAlMismoToken() {
        DispositivoMovil primero = dispositivo(1L, "ExponentPushToken[token-unico]");
        DispositivoMovil duplicado = dispositivo(2L, "ExponentPushToken[token-unico]");
        when(dispositivoRepository.findByUsuarioIdAndActivoTrueOrderByUltimoUsoDesc(8L))
                .thenReturn(List.of(primero, duplicado));
        when(provider.enviar(any())).thenReturn(PushResult.aceptado("ticket-1"));

        PushDispatchSummary summary = service.notificarUsuario(
                8L, "Pedido confirmado", "Tu pedido está confirmado",
                "techstore://pedido/1054", Map.of("pedidoId", 1054L));

        assertEquals(1, summary.dispositivos());
        assertEquals(1, summary.aceptados());
        verify(provider).enviar(any());
    }

    @Test
    void invalidaTokenQueExpoMarcaComoNoRegistrado() {
        DispositivoMovil dispositivo = dispositivo(1L, "ExponentPushToken[vencido]");
        when(dispositivoRepository.findByUsuarioIdAndActivoTrueOrderByUltimoUsoDesc(8L))
                .thenReturn(List.of(dispositivo));
        when(provider.enviar(any())).thenReturn(PushResult.error("DeviceNotRegistered", true));

        PushDispatchSummary summary = service.notificarUsuario(
                8L, "Mensaje nuevo", "Tenés una respuesta",
                "techstore://chat/89", Map.of("conversationId", 89L));

        assertEquals(1, summary.fallidos());
        assertFalse(dispositivo.getActivo());
        verify(dispositivoRepository).save(dispositivo);
    }

    @Test
    void unaCaidaDelProveedorNoPropagaLaExcepcion() {
        DispositivoMovil dispositivo = dispositivo(1L, "ExponentPushToken[temporal]");
        when(dispositivoRepository.findByUsuarioIdAndActivoTrueOrderByUltimoUsoDesc(8L))
                .thenReturn(List.of(dispositivo));
        when(provider.enviar(any())).thenThrow(new IllegalStateException("red caída"));

        PushDispatchSummary summary = assertDoesNotThrow(() -> service.notificarUsuario(
                8L, "Pedido", "Actualización", "techstore://pedido/1", Map.of()));

        assertEquals(1, summary.fallidos());
        verify(dispositivoRepository, never()).save(any());
    }

    private DispositivoMovil dispositivo(Long id, String token) {
        Usuario usuario = new Usuario();
        usuario.setId(8L);
        DispositivoMovil dispositivo = new DispositivoMovil();
        dispositivo.setId(id);
        dispositivo.setUsuario(usuario);
        dispositivo.setPushToken(token);
        dispositivo.setActivo(true);
        dispositivo.setUltimoUso(LocalDateTime.now());
        return dispositivo;
    }
}
