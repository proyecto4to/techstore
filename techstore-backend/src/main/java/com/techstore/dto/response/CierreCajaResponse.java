package com.techstore.dto.response;

import com.techstore.entity.CierreCaja;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CierreCajaResponse(
        Long id,
        Integer numero,
        LocalDateTime fechaDesde,
        LocalDateTime fechaCierre,
        String usuarioEmail,
        BigDecimal saldoInicial,
        BigDecimal ingresosEfectivo,
        BigDecimal egresosEfectivo,
        BigDecimal ingresosTransferencia,
        BigDecimal efectivoTeorico,
        BigDecimal efectivoContado,
        BigDecimal diferencia,
        String notas) {

    public static CierreCajaResponse from(CierreCaja c) {
        return new CierreCajaResponse(
                c.getId(),
                c.getNumero(),
                c.getFechaDesde(),
                c.getFechaCierre(),
                c.getUsuario().getEmail(),
                c.getSaldoInicial(),
                c.getIngresosEfectivo(),
                c.getEgresosEfectivo(),
                c.getIngresosTransferencia(),
                c.getEfectivoTeorico(),
                c.getEfectivoContado(),
                c.getDiferencia(),
                c.getNotas());
    }
}
