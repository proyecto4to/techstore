package com.techstore.vehiculos;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record VehiculoRequest(
        @NotBlank @Size(max = 20) String matricula,
        @NotBlank @Size(max = 80) String marca,
        @NotBlank @Size(max = 80) String modelo,
        @Min(1900) @Max(2200) Integer anio,
        @NotBlank @Size(max = 40) String tipo,
        @Size(max = 40) String color,
        @Size(max = 100) String numeroChasis,
        @NotNull @DecimalMin("0") BigDecimal kilometrajeActual,
        @Size(max = 40) String tipoCombustible,
        @NotNull EstadoVehiculo estado,
        @Size(max = 2000) String observaciones) {
}
