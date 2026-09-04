package com.techstore.caja;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Un cierre de caja ya registrado.
 *
 * <p>Los campos nulos se omiten: a un cajero se le devuelve la version sin
 * arqueo y esas claves no tienen que llegar ni vacias.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
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

    /**
     * El mismo cierre sin el resultado del arqueo.
     *
     * <p>Se va la diferencia, que es el resultado, y tambien el efectivo
     * teorico y los totales de movimientos: con {@code saldoInicial +
     * ingresosEfectivo - egresosEfectivo} el teorico se reconstruye, y
     * teniendolo junto a {@code efectivoContado} la diferencia sale de una
     * resta. Queda lo que el cajero declaro y los datos del turno.
     */
    public CierreCajaResponse sinArqueo() {
        return new CierreCajaResponse(
                id,
                numero,
                fechaDesde,
                fechaCierre,
                usuarioEmail,
                moneda,
                null,
                null,
                null,
                null,
                null,
                null,
                efectivoContado,
                null,
                notas);
    }
}
