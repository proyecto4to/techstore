package com.techstore.vehiculos;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.Map;

public record EventoVehiculoRequest(
        @NotNull TipoEventoVehiculo tipo,
        @NotNull @DecimalMin("0") BigDecimal kilometraje,
        @NotBlank @Size(max = 4000) String descripcion,
        @Size(max = 255) String ubicacion,
        @DecimalMin("0") BigDecimal importe,
        Map<String, Object> datosAdicionales,
        @Size(max = 2000) String observacion,
        Boolean enviarAMantenimiento) {
}
