package com.techstore.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CheckoutRequest(
        @NotEmpty @Valid List<ItemCarrito> items,
        @Size(max = 1000) String notas) {

    public record ItemCarrito(
            @NotNull Long productoId,
            @NotNull @Min(1) Integer cantidad) {
    }
}
