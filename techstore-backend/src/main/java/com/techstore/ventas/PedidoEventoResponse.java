package com.techstore.ventas;

import java.time.LocalDateTime;

/**
 * Un paso del historial del pedido.
 *
 * <p>No lleva quién lo hizo: al cliente le sirve saber que el pago se aprobó,
 * no el nombre de quien lo revisó.
 */
public record PedidoEventoResponse(String tipo, String detalle, LocalDateTime fecha) {

    static PedidoEventoResponse from(PedidoEvento evento) {
        return new PedidoEventoResponse(evento.getTipo().name(), evento.getDetalle(), evento.getCreatedAt());
    }
}
