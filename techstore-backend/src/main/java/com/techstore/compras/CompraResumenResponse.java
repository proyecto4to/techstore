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
        /** Correo del proveedor, para saber si se le puede mandar el pedido. */
        String proveedorEmail,
        /** Cuándo se le mandó el pedido al proveedor. Nulo = todavía no salió. */
        LocalDateTime enviadoEn,
        String enviadoCanal,
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
                pedido.getProveedor() == null ? null : pedido.getProveedor().getEmail(),
                pedido.getEnviadoEn(),
                pedido.getEnviadoCanal() == null ? null : pedido.getEnviadoCanal().etiqueta(),
                pedido.getUsuario().getEmail());
    }
}
