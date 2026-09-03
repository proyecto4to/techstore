package com.techstore.ventas;

import com.techstore.catalogo.Producto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ValidarCarritoRequest(
        @NotEmpty @Size(max = 50) List<@Valid Item> items) {

    public record Item(
            @NotNull @Positive Long productoId,
            @NotNull @Min(1) @Max(Producto.MAXIMO_POR_PEDIDO) Integer cantidad) {
    }
}
