package com.techstore.vehiculos;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record UsoVehiculoResponse(
        Long id,
        Long vehiculoId,
        String vehiculo,
        Long repartidorId,
        String repartidor,
        BigDecimal kmInicio,
        BigDecimal combustibleInicio,
        LocalDateTime fechaInicio,
        BigDecimal kmFin,
        BigDecimal combustibleFin,
        LocalDateTime fechaFin,
        String estado,
        String observacionInicio,
        String observacionFin) {
    public static UsoVehiculoResponse from(UsoVehiculo u) {
        return new UsoVehiculoResponse(u.getId(), u.getVehiculo().getId(),
                u.getVehiculo().getMatricula() + " · " + u.getVehiculo().getMarca() + " " + u.getVehiculo().getModelo(),
                u.getRepartidor().getId(),
                (u.getRepartidor().getNombre() + " " + u.getRepartidor().getApellido()).trim(),
                u.getKmInicio(), u.getCombustibleInicio(), u.getFechaInicio(), u.getKmFin(),
                u.getCombustibleFin(), u.getFechaFin(), u.getEstado().name(),
                u.getObservacionInicio(), u.getObservacionFin());
    }
}
