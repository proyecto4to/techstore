package com.techstore.facturacion;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record NotaCreditoResponse(
        Long id,
        String numero,
        String facturaNumero,
        Integer pedidoNumero,
        String motivo,
        String moneda,
        BigDecimal subtotal,
        BigDecimal iva,
        BigDecimal total,
        boolean pedidoDevueltoTotal,
        LocalDateTime fecha,
        String usuarioEmail,
        List<DetalleResponse> detalles) {

    public record DetalleResponse(
            String productoNombre,
            Integer cantidad,
            BigDecimal precioUnitario,
            BigDecimal subtotal) {
    }

    public static NotaCreditoResponse from(NotaCredito nc) {
        boolean devueltoTotal = nc.getPedido() != null
                && nc.getPedido().getEstado() == com.techstore.ventas.EstadoPedido.R;
        return new NotaCreditoResponse(
                nc.getId(),
                nc.getNumero(),
                nc.getFactura().getNumero(),
                nc.getPedido().getNumero(),
                nc.getMotivo(),
                nc.getMoneda().codigo(),
                nc.getSubtotal().monto(),
                nc.getIva().monto(),
                nc.getTotal().monto(),
                devueltoTotal,
                nc.getFecha(),
                nc.getUsuario().getEmail(),
                nc.getDetalles().stream()
                        .map(d -> new DetalleResponse(
                                d.getProducto().getNombre(),
                                d.getCantidad(),
                                d.getPrecioUnitario().monto(),
                                d.getSubtotal().monto()))
                        .toList());
    }
}
