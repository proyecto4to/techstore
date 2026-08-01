package com.techstore.compras;

import com.techstore.ventas.Pedido;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CompraResumenResponse(
        Long id,
        Integer numero,
        String estado,
        boolean pagada,
        LocalDateTime fecha,
        String moneda,
        BigDecimal total,
        String proveedorRazonSocial,
        String usuarioEmail) {

    public static CompraResumenResponse from(Pedido pedido) {
        return new CompraResumenResponse(
                pedido.getId(),
                pedido.getNumero(),
                pedido.getEstado().name(),
                Boolean.TRUE.equals(pedido.getPagada()),
                pedido.getFecha(),
                pedido.getMoneda().codigo(),
                pedido.getTotal().monto(),
                pedido.getProveedor() == null ? null : pedido.getProveedor().getRazonSocial(),
                pedido.getUsuario().getEmail());
    }
}
