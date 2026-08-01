package com.techstore.reportes;

import java.math.BigDecimal;
import java.util.List;

/**
 * Indicadores de un vistazo para el panel de Admin/Cajero.
 */
public record DashboardResponse(
        BigDecimal ventasHoy,
        long facturasHoy,
        long pedidosPendientes,
        long pedidosPorCobrar,
        long stockBajoCount,
        List<StockBajoResponse> stockBajo,
        BigDecimal efectivoTurno,
        BigDecimal transferenciasTurno) {
}
