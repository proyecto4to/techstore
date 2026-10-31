package com.techstore.notificaciones.outbox;

public enum NotificationEventType {
    PEDIDO_CREADO,
    PEDIDO_CONFIRMADO,
    PEDIDO_CANCELADO,
    PAGO_CONFIRMADO,
    /** Llegó un comprobante de transferencia y espera que la tienda lo revise. */
    PAGO_EN_VERIFICACION,
    /** La tienda no pudo validar el comprobante; el cliente puede mandar otro. */
    PAGO_RECHAZADO,
    PEDIDO_PREPARADO,
    /** El pedido para retirar en el local ya está separado y esperando. */
    PEDIDO_LISTO_RETIRO,
    ENVIO_DESPACHADO,
    ENVIO_EN_CAMINO,
    ENVIO_ENTREGADO,
    MENSAJE_NUEVO,
    REPARTO_ASIGNADO,
    REPARTO_REASIGNADO,
    REPARTO_ACTUALIZADO,
    TRANSFERENCIA_SOLICITADA,
    TRANSFERENCIA_ACEPTADA,
    TRANSFERENCIA_RECHAZADA,
    VEHICULO_ASIGNADO,
    VEHICULO_PROBLEMA
}
