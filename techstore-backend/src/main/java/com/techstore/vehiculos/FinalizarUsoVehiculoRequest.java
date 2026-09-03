package com.techstore.vehiculos;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record FinalizarUsoVehiculoRequest(
        @NotNull @DecimalMin("0") BigDecimal kilometrajeFinal,
        @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal combustibleFinal,
        @Size(max = 1000) String observacion) {
}
