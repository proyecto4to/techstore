package com.techstore.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ResumenCajaResponse(
        LocalDate desde,
        LocalDate hasta,
        BigDecimal ingresos,
        BigDecimal egresos,
        BigDecimal saldo) {
}
