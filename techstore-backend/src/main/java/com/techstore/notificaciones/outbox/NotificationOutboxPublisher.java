package com.techstore.notificaciones.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.techstore.comun.tenant.TenantContext;
import com.techstore.envios.Envio;
import com.techstore.facturacion.Factura;
import com.techstore.mensajeria.Mensaje;
import com.techstore.ventas.Pedido;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Collection;

/** Inserta el evento en la misma transacción que el cambio de negocio. */
@Service
@RequiredArgsConstructor
public class NotificationOutboxPublisher {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public void pedidoCreado(Pedido pedido) {
        publicarUsuario(NotificationEventType.PEDIDO_CREADO, "PEDIDO", pedido.getId(),
                new NotificationPayload(pedido.getUsuario().getId(), pedido.getId(), null, null, null, null));
    }

    public void pedidoConfirmado(Pedido pedido) {
        publicarUsuario(NotificationEventType.PEDIDO_CONFIRMADO, "PEDIDO", pedido.getId(),
                new NotificationPayload(pedido.getUsuario().getId(), pedido.getId(), null, null, null, null));
    }

    public void pedidoCancelado(Pedido pedido) {
        publicarUsuario(NotificationEventType.PEDIDO_CANCELADO, "PEDIDO", pedido.getId(),
                new NotificationPayload(pedido.getUsuario().getId(), pedido.getId(), null, null, null, null));
    }

    public void pagoConfirmado(Factura factura, boolean notificarCuenta) {
        Long usuarioId = notificarCuenta ? factura.getPedido().getUsuario().getId() : null;
        NotificationPayload payload = new NotificationPayload(
                usuarioId, factura.getPedido().getId(), factura.getId(), null, null, null);
        if (notificarCuenta || factura.getReceptorEmail() != null) {
            publicar(NotificationEventType.PAGO_CONFIRMADO, OutboxChannel.EMAIL,
                    "FACTURA", factura.getId(), payload);
        }
        if (notificarCuenta) {
            publicar(NotificationEventType.PAGO_CONFIRMADO, OutboxChannel.PUSH,
                    "FACTURA", factura.getId(), payload);
            publicar(NotificationEventType.PAGO_CONFIRMADO, OutboxChannel.INTERNAL,
                    "FACTURA", factura.getId(), payload);
        }
    }

    public void envioActualizado(NotificationEventType tipo, Envio envio) {
        publicarUsuario(tipo, "ENVIO", envio.getId(), new NotificationPayload(
                envio.getPedido().getUsuario().getId(), envio.getPedido().getId(), null,
                envio.getId(), null, null));
    }

    public void mensajeNuevo(Mensaje mensaje, Collection<Long> destinatarios) {
        for (Long usuarioId : destinatarios) {
            NotificationPayload payload = new NotificationPayload(usuarioId,
                    mensaje.getConversacion().getPedido() == null
                            ? null : mensaje.getConversacion().getPedido().getId(),
                    null, null, mensaje.getConversacion().getId(), mensaje.getId());
            publicarUsuario(NotificationEventType.MENSAJE_NUEVO, "MENSAJE", mensaje.getId(), payload);
        }
    }

    private void publicarUsuario(NotificationEventType tipo, String agregado, Long agregadoId,
                                 NotificationPayload payload) {
        publicar(tipo, OutboxChannel.EMAIL, agregado, agregadoId, payload);
        publicar(tipo, OutboxChannel.PUSH, agregado, agregadoId, payload);
        publicar(tipo, OutboxChannel.INTERNAL, agregado, agregadoId, payload);
    }

    private void publicar(NotificationEventType tipo, OutboxChannel canal, String agregado,
                          Long agregadoId, NotificationPayload payload) {
        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("No se pudo serializar la referencia de notificación", exception);
        }
        Long destinatario = payload.recipientUserId();
        String clave = tipo + ":" + agregado + ":" + agregadoId + ":U"
                + (destinatario == null ? "0" : destinatario) + ":" + canal;
        jdbcTemplate.update("""
                INSERT INTO notification_outbox
                    (tenant_id, event_type, consumer_channel, aggregate_type, aggregate_id,
                     recipient_user_id, payload, status, attempts, next_attempt_at, idempotency_key)
                VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, 'PENDING', 0, CURRENT_TIMESTAMP, ?)
                ON CONFLICT (tenant_id, idempotency_key) DO NOTHING
                """, TenantContext.actual(), tipo.name(), canal.name(), agregado, agregadoId,
                destinatario, json, clave);
    }
}
