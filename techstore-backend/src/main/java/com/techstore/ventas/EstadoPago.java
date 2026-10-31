package com.techstore.ventas;

/**
 * Dónde está la verificación del pago de un pedido.
 *
 * <p>Va aparte de {@link EstadoPedido}: un pedido sigue Pendiente mientras la
 * tienda mira el comprobante, y recién al aprobarlo pasa a Confirmado y
 * Pagado. Los reportes, la web y GeneXus leen el estado del pedido; meter la
 * verificación ahí los obligaba a cambiar a todos.
 */
public enum EstadoPago {
    /** Se cobra en persona: retiro con pago en el local, o pedidos anteriores a la regla. */
    NO_REQUERIDO,
    /** Eligió transferencia y todavía no mandó el respaldo. */
    PENDIENTE_COMPROBANTE,
    /** Hay al menos un comprobante esperando que la tienda lo revise. */
    EN_VERIFICACION,
    /** La tienda confirmó que la transferencia llegó: el pedido quedó cobrado. */
    APROBADO,
    /** La tienda no pudo validar el comprobante; el cliente puede mandar otro. */
    RECHAZADO;

    /** Si el pedido espera algo del cliente o de la tienda para poder cobrarse. */
    public boolean pendiente() {
        return this == PENDIENTE_COMPROBANTE || this == EN_VERIFICACION || this == RECHAZADO;
    }
}
