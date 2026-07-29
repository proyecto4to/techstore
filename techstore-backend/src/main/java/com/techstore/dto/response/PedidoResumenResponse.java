package com.techstore.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PedidoResumenResponse(
        Long id,
        Integer numero,
        String tipo,
        String estado,
        LocalDateTime fecha,
        BigDecimal total,
        String usuarioEmail,
        /** Datos fiscales del cliente, para prellenar el cobro con RUC. */
        String clienteRuc,
        String clienteRazonSocial) {
}
