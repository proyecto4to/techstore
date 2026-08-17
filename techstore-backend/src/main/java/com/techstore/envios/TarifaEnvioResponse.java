package com.techstore.envios;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TarifaEnvioResponse(
        Long id, String codigo, String nombre, String descripcion, String tipo,
        String providerCode, String transportista, String departamento, String ciudad, String zona,
        BigDecimal pesoMinKg, BigDecimal pesoMaxKg, BigDecimal subtotalMin, BigDecimal subtotalMax,
        BigDecimal costo, BigDecimal umbralEnvioGratis, BigDecimal iva,
        Integer diasEstimadosMin, Integer diasEstimadosMax, Integer prioridad,
        Boolean promocion, LocalDateTime vigenciaDesde, LocalDateTime vigenciaHasta, Boolean activa) { }
