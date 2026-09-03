package com.techstore.mensajeria;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CrearConversacionRequest(
        Long pedidoId,
        @NotBlank @Size(max = 100) String clientMessageId,
        @NotBlank @Size(max = 2000) String mensaje) {

    /** GeneXus representa un ID opcional vacío con cero. */
    public CrearConversacionRequest {
        if (Long.valueOf(0L).equals(pedidoId)) {
            pedidoId = null;
        }
    }
}
