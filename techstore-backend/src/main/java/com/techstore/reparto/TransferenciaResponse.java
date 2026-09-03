package com.techstore.reparto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransferenciaResponse(
        Long id,
        Long cierreId,
        Long repartidorId,
        String repartidor,
        Long cajeroId,
        String cajero,
        BigDecimal monto,
        String moneda,
        String estado,
        LocalDateTime fechaSolicitud,
        LocalDateTime fechaRespuesta,
        String motivoRechazo,
        String observacion) {
    public static TransferenciaResponse from(TransferenciaRepartidorCajero t) {
        return new TransferenciaResponse(t.getId(), t.getCierre().getId(), t.getRepartidor().getId(),
                nombre(t.getRepartidor()), t.getCajero().getId(), nombre(t.getCajero()),
                t.getMonto().monto(), t.getMoneda().codigo(), t.getEstado().name(),
                t.getFechaSolicitud(), t.getFechaRespuesta(), t.getMotivoRechazo(), t.getObservacion());
    }

    private static String nombre(com.techstore.usuarios.Usuario u) {
        return (u.getNombre() + " " + u.getApellido()).trim();
    }
}
