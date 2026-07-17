package com.techstore.dto.response;

import java.math.BigDecimal;

public record TopProductoResponse(
        Long productoId,
        String nombre,
        Long unidadesVendidas,
        BigDecimal montoVendido) {
}
