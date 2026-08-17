package com.techstore.notificaciones.outbox;

/** Referencias mínimas: nunca contiene correos, mensajes, tokens ni secretos. */
public record NotificationPayload(
        Long recipientUserId,
        Long pedidoId,
        Long facturaId,
        Long envioId,
        Long conversacionId,
        Long mensajeId) {
}
