package com.techstore.envios;

import com.techstore.comun.Dinero;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CotizacionEnvio(
        Long tarifaId,
        String codigo,
        String nombre,
        String descripcion,
        TipoEnvio tipo,
        String providerCode,
        String transportista,
        Dinero costo,
        BigDecimal iva,
        boolean gratis,
        BigDecimal pesoTotalKg,
        LocalDate fechaEstimadaDesde,
        LocalDate fechaEstimadaHasta) {
}
