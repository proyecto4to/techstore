package com.techstore.reparto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RechazarTransferenciaRequest(
        @NotBlank @Size(max = 1000) String motivo) {
}
