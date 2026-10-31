package com.techstore.ventas;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Un pedido con su pago por revisar, como lo ve quien cobra.
 *
 * @param comprobantesEnRevision cuántos respaldos esperan una decisión
 */
public record PagoPendienteResponse(
        Long pedidoId,
        Integer numero,
        LocalDateTime fecha,
        BigDecimal total,
        String moneda,
        String metodoEntrega,
        String estadoPago,
        String clienteNombre,
        String clienteEmail,
        long comprobantesEnRevision,
        String pagoRechazoMotivo) {
}
