package com.techstore.reparto;

import java.math.BigDecimal;

public record DashboardRepartidorResponse(
        long entregasPendientes,
        long entregasEnCurso,
        long entregasRealizadas,
        long cobrosRealizados,
        BigDecimal saldoActual,
        String moneda,
        long solicitudesPendientes) {
}
