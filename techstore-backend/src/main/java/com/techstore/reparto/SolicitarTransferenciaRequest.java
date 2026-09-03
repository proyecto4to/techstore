package com.techstore.reparto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SolicitarTransferenciaRequest(
        @NotNull Long cajeroId,
        @Size(max = 1000) String observacion) {
}
