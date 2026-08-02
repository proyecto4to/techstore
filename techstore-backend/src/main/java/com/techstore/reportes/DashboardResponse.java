package com.techstore.reportes;

import java.math.BigDecimal;
import java.util.List;

/**
 * Indicadores de un vistazo para el panel de Admin/Cajero.
 *
 * <p>Responde tres preguntas: cómo viene el día, qué hay que atender ahora y
 * qué se está vendiendo.
 */
public record DashboardResponse(
        // --- Cómo viene el día ---------------------------------------------
        BigDecimal ventasHoy,
        long facturasHoy,
        /** El mes en curso, para que el día no se mire aislado. */
        BigDecimal ventasMes,
        long facturasMes,

        // --- Qué hay que atender -------------------------------------------
        long pedidosPendientes,
        long pedidosPorCobrar,
        long stockBajoCount,
        List<StockBajoResponse> stockBajo,
        /**
         * De los que hay que reponer, cuántos ya se quedaron sin días de
         * cobertura. Esos no son un aviso: son una venta que se está perdiendo.
         */
        long sinCoberturaCount,

        // --- Qué se vende ----------------------------------------------------
        /** Los más vendidos del mes en curso, por unidades. */
        List<TopProductoResponse> topProductosMes,

        // --- Caja -------------------------------------------------------------
        BigDecimal efectivoTurno,
        BigDecimal transferenciasTurno) {
}
