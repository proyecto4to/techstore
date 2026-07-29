package com.techstore.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Arqueo del período, desglosado por medio: el efectivo está en el cajón y las
 * transferencias en el banco, así que sumarlos en un solo "saldo" no dice nada
 * sobre cuánta plata hay en la caja.
 */
public record ResumenCajaResponse(
        LocalDate desde,
        LocalDate hasta,
        BigDecimal ingresos,
        BigDecimal egresos,
        BigDecimal saldo,
        BigDecimal ingresosEfectivo,
        BigDecimal egresosEfectivo,
        BigDecimal netoEfectivo,
        BigDecimal ingresosTransferencia,
        BigDecimal egresosTransferencia,
        BigDecimal netoTransferencia) {

    /** Arma el resumen a partir de los cuatro totales, derivando netos y totales. */
    public static ResumenCajaResponse de(
            LocalDate desde, LocalDate hasta,
            BigDecimal ingresosEfectivo, BigDecimal egresosEfectivo,
            BigDecimal ingresosTransferencia, BigDecimal egresosTransferencia) {
        BigDecimal ingresos = ingresosEfectivo.add(ingresosTransferencia);
        BigDecimal egresos = egresosEfectivo.add(egresosTransferencia);
        return new ResumenCajaResponse(
                desde, hasta,
                ingresos, egresos, ingresos.subtract(egresos),
                ingresosEfectivo, egresosEfectivo, ingresosEfectivo.subtract(egresosEfectivo),
                ingresosTransferencia, egresosTransferencia,
                ingresosTransferencia.subtract(egresosTransferencia));
    }
}
