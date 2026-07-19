package com.techstore.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EmitirNotaCreditoRequest(
        @NotBlank @Size(max = 255) String motivo) {
}
