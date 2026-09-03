package com.techstore.vehiculos;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

public record EventoVehiculoResponse(
        Long id,
        Long usoVehiculoId,
        String tipo,
        LocalDateTime fecha,
        BigDecimal kilometraje,
        String descripcion,
        String ubicacion,
        BigDecimal importe,
        Map<String, Object> datosAdicionales,
        String observacion,
        Boolean requiereAtencion,
        Boolean resuelto) {
    public static EventoVehiculoResponse from(EventoVehiculo e) {
        return new EventoVehiculoResponse(e.getId(), e.getUsoVehiculo().getId(), e.getTipoEvento().name(),
                e.getFecha(), e.getKilometraje(), e.getDescripcion(), e.getUbicacion(), e.getImporte(),
                e.getDatosAdicionales(), e.getObservacion(), e.getRequiereAtencion(), e.getResuelto());
    }
}
