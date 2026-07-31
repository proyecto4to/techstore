package com.techstore.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ProductoRequest(
        @NotBlank @Size(max = 255) String nombre,
        String descripcion,
        @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal precio,
        @NotNull @Min(0) @Max(100) Integer iva,
        @NotNull @Min(0) Integer stockReal,
        /** Punto de reposición. Opcional: si no viene, queda en 0 y el producto no avisa. */
        @Min(0) Integer stockMinimo,
        Long marcaId,
        Long modeloId,
        @Size(max = 500) String imagenUrl,
        Boolean activo) {
}
