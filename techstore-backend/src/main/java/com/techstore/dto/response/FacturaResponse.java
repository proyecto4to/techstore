package com.techstore.dto.response;

import com.techstore.entity.Factura;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record FacturaResponse(
        Long id,
        String numero,
        String tipo,
        Integer pedidoNumero,
        LocalDateTime fecha,
        BigDecimal subtotal,
        BigDecimal iva,
        BigDecimal total,
        String estado) {

    public static FacturaResponse from(Factura factura) {
        return new FacturaResponse(
                factura.getId(),
                factura.getNumero(),
                factura.getTipo(),
                factura.getPedido().getNumero(),
                factura.getFecha(),
                factura.getSubtotal(),
                factura.getIva(),
                factura.getTotal(),
                factura.getEstado().name());
    }
}
