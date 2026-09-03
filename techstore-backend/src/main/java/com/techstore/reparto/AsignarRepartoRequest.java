package com.techstore.reparto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AsignarRepartoRequest(
        @NotNull Long repartidorId,
        @NotNull @Min(1) Integer ordenEntrega,
        @Size(max = 1000) String observaciones) {
}
