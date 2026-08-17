package com.techstore.direcciones;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record DireccionEntregaResponse(
        Long id,
        String nombreDestinatario,
        String telefono,
        String departamento,
        String ciudad,
        String barrio,
        String direccionLinea1,
        String direccionLinea2,
        String numeroCasa,
        String referencia,
        String codigoPostal,
        BigDecimal latitud,
        BigDecimal longitud,
        Boolean principal,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}

