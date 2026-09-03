package com.techstore.vehiculos;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record IniciarUsoVehiculoRequest(
        @NotNull @DecimalMin("0") BigDecimal kilometrajeInicial,
        @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal combustibleInicial,
        @Size(max = 1000) String observacion) {
}
