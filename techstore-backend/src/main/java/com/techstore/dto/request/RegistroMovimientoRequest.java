package com.techstore.dto.request;

import com.techstore.entity.TipoMovimiento;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record RegistroMovimientoRequest(
        @NotNull TipoMovimiento tipo,
        @NotNull @DecimalMin(value = "1", message = "El monto debe ser mayor a cero") BigDecimal monto,
        @NotBlank @Size(max = 255) String concepto,
        @Size(max = 50) String referencia,
        com.techstore.entity.TipoPago medio) {
}
