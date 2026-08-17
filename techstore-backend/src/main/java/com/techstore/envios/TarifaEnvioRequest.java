package com.techstore.envios;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TarifaEnvioRequest(
        @NotBlank @Size(max = 80) String codigo,
        @NotBlank @Size(max = 120) String nombre,
        @Size(max = 300) String descripcion,
        @NotNull TipoEnvio tipo,
        @NotBlank @Size(max = 80) String providerCode,
        @Size(max = 120) String transportista,
        @Size(max = 80) String departamento,
        @Size(max = 100) String ciudad,
        @Size(max = 100) String zona,
        @DecimalMin("0.0") BigDecimal pesoMinKg,
        @DecimalMin("0.0") BigDecimal pesoMaxKg,
        @DecimalMin("0.0") BigDecimal subtotalMin,
        @DecimalMin("0.0") BigDecimal subtotalMax,
        @NotNull @DecimalMin("0.0") BigDecimal costo,
        @DecimalMin("0.0") BigDecimal umbralEnvioGratis,
        @NotNull @Min(0) @Max(10) Integer iva,
        @NotNull @Min(0) Integer diasEstimadosMin,
        @NotNull @Min(0) Integer diasEstimadosMax,
        @NotNull Integer prioridad,
        Boolean promocion,
        LocalDateTime vigenciaDesde,
        LocalDateTime vigenciaHasta,
        Boolean activa) { }
