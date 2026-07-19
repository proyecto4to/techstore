package com.techstore.dto.response;

import com.techstore.entity.NotaCredito;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record NotaCreditoResponse(
        Long id,
        String numero,
        String facturaNumero,
        Integer pedidoNumero,
        String motivo,
        BigDecimal subtotal,
        BigDecimal iva,
        BigDecimal total,
        LocalDateTime fecha,
        String usuarioEmail) {

    public static NotaCreditoResponse from(NotaCredito nc) {
        return new NotaCreditoResponse(
                nc.getId(),
                nc.getNumero(),
                nc.getFactura().getNumero(),
                nc.getPedido().getNumero(),
                nc.getMotivo(),
                nc.getSubtotal(),
                nc.getIva(),
                nc.getTotal(),
                nc.getFecha(),
                nc.getUsuario().getEmail());
    }
}
