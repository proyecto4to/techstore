package com.techstore.notificaciones;

import com.techstore.comun.PageResponse;
import com.techstore.comun.ResourceNotFoundException;
import com.techstore.notificaciones.outbox.NotificationOutbox;
import com.techstore.usuarios.Usuario;
import com.techstore.usuarios.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class NotificacionUsuarioService {
    private final NotificacionUsuarioRepository repository;
    private final UsuarioRepository usuarioRepository;

    @Transactional
    public void registrarDesdeOutbox(NotificationOutbox outbox, Long usuarioId,
                                      String titulo, String mensaje, String deepLink) {
        if (repository.existsBySourceOutboxId(outbox.getId())) return;
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new IllegalStateException("El destinatario de la notificación ya no existe"));
        NotificacionUsuario notification = new NotificacionUsuario();
        notification.setUsuario(usuario);
        notification.setTipo(outbox.getEventType());
        notification.setTitulo(titulo);
        notification.setMensaje(mensaje);
        notification.setReferenciaTipo(outbox.getAggregateType());
        notification.setReferenciaId(outbox.getAggregateId());
        notification.setDeepLink(deepLink);
        notification.setSourceOutboxId(outbox.getId());
        notification.setLeida(false);
        repository.save(notification);
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificacionUsuarioResponse> listar(String email, Pageable pageable) {
        Usuario usuario = usuario(email);
        return PageResponse.from(repository.findByUsuarioId(usuario.getId(), pageable)
                .map(NotificacionUsuarioResponse::from));
    }

    @Transactional(readOnly = true)
    public UnreadNotificationCountResponse contarNoLeidas(String email) {
        Usuario usuario = usuario(email);
        return new UnreadNotificationCountResponse(repository.countByUsuarioIdAndLeidaFalse(usuario.getId()));
    }

    @Transactional(readOnly = true)
    public NotificacionUsuarioResponse obtener(Long id, String email) {
        Usuario usuario = usuario(email);
        return repository.findByIdAndUsuarioId(id, usuario.getId())
                .map(NotificacionUsuarioResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("notificaciones.no.encontrada", id));
    }

    @Transactional
    public NotificacionUsuarioResponse marcarLeida(Long id, String email) {
        Usuario usuario = usuario(email);
        NotificacionUsuario notification = repository.findByIdAndUsuarioId(id, usuario.getId())
                .orElseThrow(() -> new ResourceNotFoundException("notificaciones.no.encontrada", id));
        if (!notification.isLeida()) {
            notification.setLeida(true);
            notification.setLeidaEn(LocalDateTime.now());
        }
        return NotificacionUsuarioResponse.from(notification);
    }

    @Transactional
    public MarkNotificationsReadResponse marcarTodasLeidas(String email) {
        Usuario usuario = usuario(email);
        return new MarkNotificationsReadResponse(
                repository.marcarTodasLeidas(usuario.getId(), LocalDateTime.now()));
    }

    private Usuario usuario(String email) {
        return usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("Usuario no encontrado"));
    }
}
