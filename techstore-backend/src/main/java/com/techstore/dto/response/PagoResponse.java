package com.techstore.dto.response;

import com.techstore.entity.Factura;
import com.techstore.entity.Pago;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PagoResponse(
        Long id,
        Integer numero,
        Integer pedidoNumero,
        BigDecimal monto,
        String tipoPago,
        String numeroComprobante,
        LocalDateTime fecha,
        FacturaResponse factura) {

    public static PagoResponse from(Pago pago, Factura factura) {
        return new PagoResponse(
                pago.getId(),
                pago.getNumero(),
                pago.getPedido().getNumero(),
                pago.getMonto(),
                pago.getTipoPago().name(),
                pago.getNumeroComprobante(),
                pago.getFecha(),
                factura == null ? null : FacturaResponse.from(factura));
    }
}
