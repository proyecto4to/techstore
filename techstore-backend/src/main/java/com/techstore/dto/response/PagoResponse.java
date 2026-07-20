package com.techstore.dto.response;

import com.techstore.entity.Factura;
import com.techstore.entity.Pago;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record PagoResponse(
        Integer pedidoNumero,
        BigDecimal total,
        LocalDateTime fecha,
        List<LineaPagada> lineas,
        FacturaResponse factura) {

    public record LineaPagada(
            Integer numero,
            String tipoPago,
            BigDecimal monto,
            String numeroComprobante) {

        public static LineaPagada from(Pago pago) {
            return new LineaPagada(
                    pago.getNumero(),
                    pago.getTipoPago().name(),
                    pago.getMonto(),
                    pago.getNumeroComprobante());
        }
    }

    public static PagoResponse from(List<Pago> pagos, Factura factura) {
        Pago primero = pagos.get(0);
        return new PagoResponse(
                primero.getPedido().getNumero(),
                factura.getTotal(),
                primero.getFecha(),
                pagos.stream().map(LineaPagada::from).toList(),
                FacturaResponse.from(factura));
    }
}
