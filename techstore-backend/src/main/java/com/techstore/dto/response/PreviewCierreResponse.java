package com.techstore.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Vista previa del turno abierto: lo que el sistema calcula antes de que
 * el cajero cuente el efectivo físico.
 */
public record PreviewCierreResponse(
        LocalDateTime desde,
        BigDecimal saldoInicial,
        BigDecimal ingresosEfectivo,
        BigDecimal egresosEfectivo,
        BigDecimal ingresosTransferencia,
        BigDecimal efectivoTeorico) {
}
