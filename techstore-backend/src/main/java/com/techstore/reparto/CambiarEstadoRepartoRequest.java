package com.techstore.reparto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CambiarEstadoRepartoRequest(
        @NotNull EstadoReparto estado,
        @Size(max = 1000) String observacion,
        @Size(max = 1000) String resultadoFinal) {
}
