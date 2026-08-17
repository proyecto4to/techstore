package com.techstore.facturacion;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record FacturaResponse(
        Long id,
        String numero,
        /** Contado o crédito, como se lee en pantalla. */
        String condicionVenta,
        /** Timbrado con el que se emitió; nulo en las anteriores a que se registrara. */
        String timbrado,
        Integer pedidoNumero,
        LocalDateTime fecha,
        String moneda,
        BigDecimal subtotal,
        BigDecimal iva,
        BigDecimal total,
        String estado,
        /** RUC al que se emitió, o null si salió a consumidor final. */
        String receptorRuc,
        String receptorNombre,
        String receptorEmail) {

    public static FacturaResponse from(Factura factura) {
        return new FacturaResponse(
                factura.getId(),
                factura.getNumero(),
                factura.getCondicionVenta().etiqueta(),
                factura.getTimbrado(),
                factura.getPedido().getNumero(),
                factura.getFecha(),
                factura.getMoneda().codigo(),
                factura.getSubtotal().monto(),
                factura.getIva().monto(),
                factura.getTotal().monto(),
                factura.getEstado().name(),
                factura.getReceptorRuc(),
                factura.getReceptorNombre(),
                factura.getReceptorEmail());
    }
}
