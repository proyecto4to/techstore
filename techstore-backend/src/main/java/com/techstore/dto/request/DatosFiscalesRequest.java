package com.techstore.dto.request;

import jakarta.validation.constraints.Size;

/**
 * Datos fiscales propios del cliente. Se mandan vacíos para volver a facturar
 * como consumidor final.
 */
public record DatosFiscalesRequest(
        @Size(max = 20) String ruc,
        @Size(max = 255) String razonSocial) {
}
