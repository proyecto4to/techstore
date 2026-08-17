package com.techstore.notificaciones.outbox;

public enum NotificationEventType {
    PEDIDO_CREADO,
    PEDIDO_CONFIRMADO,
    PEDIDO_CANCELADO,
    PAGO_CONFIRMADO,
    PEDIDO_PREPARADO,
    ENVIO_DESPACHADO,
    ENVIO_EN_CAMINO,
    ENVIO_ENTREGADO,
    MENSAJE_NUEVO
}
