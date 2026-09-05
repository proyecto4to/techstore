package com.techstore.mensajeria;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param destinatarioId con quién se quiere hablar. Nulo abre —o retoma— la
 *                       conversación de soporte con la tienda, que es lo único
 *                       que puede hacer un cliente.
 */
public record CrearConversacionRequest(
        Long pedidoId,
        Long destinatarioId,
        @NotBlank @Size(max = 100) String clientMessageId,
        @NotBlank @Size(max = 2000) String mensaje) {

    /** GeneXus representa un ID opcional vacío con cero. */
    public CrearConversacionRequest {
        if (Long.valueOf(0L).equals(pedidoId)) {
            pedidoId = null;
        }
        if (Long.valueOf(0L).equals(destinatarioId)) {
            destinatarioId = null;
        }
    }
}
