package com.techstore.entity;

/**
 * Estados del ciclo de vida de un pedido. Los nombres de las constantes
 * coinciden con las etiquetas del tipo enum "estado_pedido" en PostgreSQL.
 */
public enum EstadoPedido {
    /** Pendiente: recién creado, stock comprometido. */
    P,
    /** Confirmado: stock descontado del inventario real. */
    C,
    /** Pagado: tiene un pago registrado y factura emitida. */
    G,
    /** Entregado. */
    N,
    /** Rechazado. */
    R,
    /** Anulado: stock comprometido liberado. */
    X
}
