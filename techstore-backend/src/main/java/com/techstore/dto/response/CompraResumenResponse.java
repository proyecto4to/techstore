package com.techstore.dto.response;

import com.techstore.entity.Pedido;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CompraResumenResponse(
        Long id,
        Integer numero,
        String estado,
        LocalDateTime fecha,
        BigDecimal total,
        String proveedorRazonSocial,
        String usuarioEmail) {

    public static CompraResumenResponse from(Pedido pedido) {
        return new CompraResumenResponse(
                pedido.getId(),
                pedido.getNumero(),
                pedido.getEstado().name(),
                pedido.getFecha(),
                pedido.getTotal(),
                pedido.getProveedor() == null ? null : pedido.getProveedor().getRazonSocial(),
                pedido.getUsuario().getEmail());
    }
}
