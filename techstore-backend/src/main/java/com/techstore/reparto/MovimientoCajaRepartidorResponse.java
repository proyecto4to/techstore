package com.techstore.reparto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record MovimientoCajaRepartidorResponse(
        Long id,
        String tipo,
        String naturaleza,
        BigDecimal monto,
        String moneda,
        String referenciaTipo,
        Long referenciaId,
        LocalDateTime fecha,
        String estado,
        String observacion) {
    public static MovimientoCajaRepartidorResponse from(MovimientoCajaRepartidor m) {
        return new MovimientoCajaRepartidorResponse(m.getId(), m.getTipoMovimiento().name(),
                m.getNaturaleza().name(), m.getMonto().monto(), m.getMoneda().codigo(),
                m.getReferenciaTipo(), m.getReferenciaId(), m.getFecha(), m.getEstado().name(),
                m.getObservacion());
    }
}
