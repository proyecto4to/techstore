package com.techstore.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CerrarCajaRequest(
        @NotNull @DecimalMin(value = "0", message = "El efectivo contado no puede ser negativo")
        BigDecimal efectivoContado,
        @Size(max = 1000) String notas) {
}
