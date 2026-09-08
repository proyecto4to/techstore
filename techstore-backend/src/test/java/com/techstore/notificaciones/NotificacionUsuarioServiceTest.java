package com.techstore.notificaciones;

import com.techstore.comun.EnvioTrasCommit;
import com.techstore.comun.ResourceNotFoundException;
import com.techstore.notificaciones.outbox.NotificationEventType;
import com.techstore.notificaciones.outbox.NotificationOutbox;
import com.techstore.usuarios.Usuario;
import com.techstore.usuarios.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificacionUsuarioServiceTest {
    @Mock private NotificacionUsuarioRepository repository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private EnvioTrasCommit envio;

    @Test
    void internalOutboxCreatesOneNotificationWithOnlyTrustedReferences() {
        Usuario user = user(8L, "cliente@techstore.py");
        NotificationOutbox outbox = outbox(41L);
        when(repository.existsBySourceOutboxId(41L)).thenReturn(false);
        when(usuarioRepository.findById(8L)).thenReturn(Optional.of(user));
        NotificacionUsuarioService service = new NotificacionUsuarioService(repository, usuarioRepository, envio);

        service.registrarDesdeOutbox(outbox, 8L, "Pedido confirmado",
                "Tu pedido ya está en preparación.", "techstore://pedido/90");

        ArgumentCaptor<NotificacionUsuario> captor = ArgumentCaptor.forClass(NotificacionUsuario.class);
        verify(repository).save(captor.capture());
        NotificacionUsuario saved = captor.getValue();
        assertEquals(user, saved.getUsuario());
        assertEquals(NotificationEventType.PEDIDO_CONFIRMADO, saved.getTipo());
        assertEquals("PEDIDO", saved.getReferenciaTipo());
        assertEquals(90L, saved.getReferenciaId());
        assertEquals(41L, saved.getSourceOutboxId());
        assertFalse(saved.isLeida());
    }

    @Test
    void retryOfSameOutboxIsIdempotent() {
        NotificationOutbox outbox = outbox(41L);
        when(repository.existsBySourceOutboxId(41L)).thenReturn(true);
        NotificacionUsuarioService service = new NotificacionUsuarioService(repository, usuarioRepository, envio);

        service.registrarDesdeOutbox(outbox, 8L, "Título", "Mensaje", "techstore://pedido/90");

        verify(usuarioRepository, never()).findById(8L);
        verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void userCannotMarkAnotherUsersNotification() {
        Usuario user = user(8L, "cliente@techstore.py");
        when(usuarioRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(repository.findByIdAndUsuarioId(99L, 8L)).thenReturn(Optional.empty());
        NotificacionUsuarioService service = new NotificacionUsuarioService(repository, usuarioRepository, envio);

        assertThrows(ResourceNotFoundException.class, () -> service.marcarLeida(99L, user.getEmail()));
    }

    /**
     * La campana se actualizaba solo al entrar a la pantalla: la notificación
     * se guardaba y nadie avisaba. El empujón va a la cola personal porque el
     * flujo de eventos de la empresa lo escucha únicamente quien opera la
     * tienda, así que un cliente jamás se enteraba de la suya.
     */
    @Test
    void avisaPorLaColaPersonalDelDestinatario() {
        Usuario user = user(8L, "cliente@techstore.py");
        when(repository.existsBySourceOutboxId(41L)).thenReturn(false);
        when(usuarioRepository.findById(8L)).thenReturn(Optional.of(user));
        NotificacionUsuarioService service =
                new NotificacionUsuarioService(repository, usuarioRepository, envio);

        service.registrarDesdeOutbox(outbox(41L), 8L, "Pedido confirmado",
                "Tu pedido ya está en preparación.", "techstore://pedido/90");

        ArgumentCaptor<NotificacionUsuarioResponse> cuerpo =
                ArgumentCaptor.forClass(NotificacionUsuarioResponse.class);
        verify(envio).aCadaUno(eq(List.of("cliente@techstore.py")),
                eq("/queue/notifications"), cuerpo.capture());
        assertEquals("techstore://pedido/90", cuerpo.getValue().deepLink());
    }

    /** Con el teléfono y la web abiertos, el contador tiene que bajar en los dos. */
    @Test
    void leerTambienAvisa() {
        Usuario user = user(8L, "cliente@techstore.py");
        NotificacionUsuario notificacion = new NotificacionUsuario();
        notificacion.setUsuario(user);
        notificacion.setTipo(NotificationEventType.PEDIDO_CONFIRMADO);
        notificacion.setLeida(false);
        when(usuarioRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(repository.findByIdAndUsuarioId(5L, 8L)).thenReturn(Optional.of(notificacion));
        NotificacionUsuarioService service =
                new NotificacionUsuarioService(repository, usuarioRepository, envio);

        service.marcarLeida(5L, user.getEmail());

        verify(envio).aCadaUno(eq(List.of("cliente@techstore.py")),
                eq("/queue/notifications"), org.mockito.ArgumentMatchers.any());
    }

    private Usuario user(Long id, String email) {
        Usuario user = new Usuario();
        user.setId(id);
        user.setEmail(email);
        return user;
    }

    private NotificationOutbox outbox(Long id) {
        NotificationOutbox outbox = new NotificationOutbox();
        outbox.setId(id);
        outbox.setEventType(NotificationEventType.PEDIDO_CONFIRMADO);
        outbox.setAggregateType("PEDIDO");
        outbox.setAggregateId(90L);
        return outbox;
    }
}
