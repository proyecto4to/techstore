package com.techstore.ventas;

/** Lo que puede pasarle a un pedido, tal como se cuenta en su historial. */
public enum TipoEventoPedido {
    CREADO,
    COMPROBANTE_ADJUNTO,
    COMPROBANTE_QUITADO,
    PAGO_APROBADO,
    PAGO_RECHAZADO,
    CONFIRMADO,
    CANCELADO,
    ENTREGADO
}
