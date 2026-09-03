package com.techstore.vehiculos;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record VehiculoResponse(
        Long id,
        String matricula,
        String marca,
        String modelo,
        Integer anio,
        String tipo,
        String color,
        String numeroChasis,
        BigDecimal kilometrajeActual,
        String tipoCombustible,
        String estado,
        Boolean activo,
        String observaciones,
        LocalDateTime fechaAlta,
        Long usoActivoId,
        Long repartidorId,
        String utilizadoPor,
        Boolean seleccionable) {
    public static VehiculoResponse from(Vehiculo v, UsoVehiculo uso) {
        return new VehiculoResponse(v.getId(), v.getMatricula(), v.getMarca(), v.getModelo(), v.getAnio(),
                v.getTipo(), v.getColor(), v.getNumeroChasis(), v.getKilometrajeActual(),
                v.getTipoCombustible(), v.getEstado().name(), v.getActivo(), v.getObservaciones(),
                v.getFechaAlta(), uso == null ? null : uso.getId(),
                uso == null ? null : uso.getRepartidor().getId(),
                uso == null ? null : (uso.getRepartidor().getNombre() + " "
                        + uso.getRepartidor().getApellido()).trim(),
                Boolean.TRUE.equals(v.getActivo()) && v.getEstado() == EstadoVehiculo.DISPONIBLE && uso == null);
    }
}
