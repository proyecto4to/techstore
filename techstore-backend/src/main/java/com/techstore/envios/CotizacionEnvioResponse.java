package com.techstore.envios;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CotizacionEnvioResponse(
        Long tarifaId,
        String codigo,
        String nombre,
        String descripcion,
        String tipo,
        String providerCode,
        String transportista,
        BigDecimal costo,
        String moneda,
        BigDecimal iva,
        boolean gratis,
        BigDecimal pesoTotalKg,
        LocalDate fechaEstimadaDesde,
        LocalDate fechaEstimadaHasta) {
    static CotizacionEnvioResponse desde(CotizacionEnvio c) {
        return new CotizacionEnvioResponse(c.tarifaId(), c.codigo(), c.nombre(), c.descripcion(),
                c.tipo().name(), c.providerCode(), c.transportista(), c.costo().monto(),
                c.costo().moneda().codigo(), c.iva(), c.gratis(), c.pesoTotalKg(),
                c.fechaEstimadaDesde(), c.fechaEstimadaHasta());
    }
}
