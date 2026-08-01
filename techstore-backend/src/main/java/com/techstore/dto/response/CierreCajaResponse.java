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
        String moneda,
        BigDecimal saldoInicial,
        BigDecimal ingresosEfectivo,
        BigDecimal egresosEfectivo,
        BigDecimal ingresosTransferencia,
        BigDecimal egresosTransferencia,
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
                c.getMoneda().codigo(),
                c.getSaldoInicial().monto(),
                c.getIngresosEfectivo().monto(),
                c.getEgresosEfectivo().monto(),
                c.getIngresosTransferencia().monto(),
                c.getEgresosTransferencia().monto(),
                c.getEfectivoTeorico().monto(),
                c.getEfectivoContado().monto(),
                c.getDiferencia().monto(),
                c.getNotas());
    }
}
