package com.techstore.notificaciones.outbox;

/** Referencias mínimas: nunca contiene correos, mensajes, tokens ni secretos. */
public record NotificationPayload(
        Long recipientUserId,
        Long pedidoId,
        Long facturaId,
        Long envioId,
        Long conversacionId,
        Long mensajeId,
        Long repartoId,
        Long transferenciaId,
        Long vehiculoId,
        String titulo,
        String mensaje,
        String deepLink) {

    public NotificationPayload(Long recipientUserId, Long pedidoId, Long facturaId,
                               Long envioId, Long conversacionId, Long mensajeId) {
        this(recipientUserId, pedidoId, facturaId, envioId, conversacionId, mensajeId,
                null, null, null, null, null, null);
    }
}
