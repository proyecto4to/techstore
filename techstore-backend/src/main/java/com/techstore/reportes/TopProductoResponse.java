package com.techstore.reportes;

import java.math.BigDecimal;

public record TopProductoResponse(
        Long productoId,
        String nombre,
        Long unidadesVendidas,
        BigDecimal montoVendido) {
}
