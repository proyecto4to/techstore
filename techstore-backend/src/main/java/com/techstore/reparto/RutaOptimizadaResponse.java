package com.techstore.reparto;

import java.math.BigDecimal;
import java.util.List;

public record RutaOptimizadaResponse(
        BigDecimal distanciaTotalKm,
        Integer tiempoEstimadoMinutos,
        List<Punto> puntos,
        List<Long> asignacionesSinUbicacion) {
    public record Punto(
            int orden,
            Long asignacionId,
            Long pedidoId,
            Integer pedidoNumero,
            String cliente,
            BigDecimal latitud,
            BigDecimal longitud,
            BigDecimal distanciaDesdeAnteriorKm) {
    }
}
