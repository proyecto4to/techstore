package com.techstore.compras;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record CompraRequest(
        @NotNull Long proveedorId,
        @NotEmpty @Valid List<ItemCompra> items,
        @Size(max = 1000) String notas) {

    public record ItemCompra(
            @NotNull Long productoId,
            @NotNull @Min(1) Integer cantidad,
            @NotNull @DecimalMin(value = "0.01", message = "{validacion.costo.positivo}")
            BigDecimal costoUnitario) {
    }
}
