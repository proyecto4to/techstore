package com.techstore.envios;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record CotizarEnvioRequest(
        Long direccionEntregaId,
        @NotEmpty @Valid List<Item> items) {
    public record Item(@NotNull Long productoId, @NotNull @Min(1) Integer cantidad) { }
}
