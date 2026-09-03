package com.techstore.reparto;

import java.time.LocalDateTime;

public record HistorialRepartoResponse(
        Long id,
        String estadoAnterior,
        String estadoNuevo,
        String usuario,
        LocalDateTime fecha,
        String observacion) {
    public static HistorialRepartoResponse from(RepartoHistorial h) {
        return new HistorialRepartoResponse(h.getId(),
                h.getEstadoAnterior() == null ? null : h.getEstadoAnterior().name(),
                h.getEstadoNuevo().name(), h.getUsuario().getEmail(), h.getFecha(), h.getObservacion());
    }
}
