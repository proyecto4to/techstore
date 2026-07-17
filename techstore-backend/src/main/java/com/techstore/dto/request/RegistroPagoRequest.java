package com.techstore.dto.request;

import com.techstore.entity.TipoPago;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegistroPagoRequest(
        @NotNull TipoPago tipoPago,
        @Size(max = 50) String numeroComprobante) {
}
