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
        String estado,
        /** RUC al que se emitió, o null si salió a consumidor final. */
        String receptorRuc,
        String receptorNombre) {

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
                factura.getEstado().name(),
                factura.getReceptorRuc(),
                factura.getReceptorNombre());
    }
}
