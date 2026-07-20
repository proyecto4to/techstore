package com.techstore.dto.response;

import com.techstore.entity.MovimientoCaja;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record MovimientoCajaResponse(
        Long id,
        Integer numero,
        String tipo,
        String medio,
        BigDecimal monto,
        String concepto,
        String referencia,
        LocalDateTime fecha,
        String usuarioEmail) {

    public static MovimientoCajaResponse from(MovimientoCaja movimiento) {
        return new MovimientoCajaResponse(
                movimiento.getId(),
                movimiento.getNumero(),
                movimiento.getTipo().name(),
                movimiento.getMedio() == null ? null : movimiento.getMedio().name(),
                movimiento.getMonto(),
                movimiento.getConcepto(),
                movimiento.getReferencia(),
                movimiento.getFecha(),
                movimiento.getUsuario().getEmail());
    }
}
