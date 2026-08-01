package com.techstore.caja;

import com.techstore.comun.Dinero;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Arqueo del período, desglosado por medio: el efectivo está en el cajón y las
 * transferencias en el banco, así que sumarlos en un solo "saldo" no dice nada
 * sobre cuánta plata hay en la caja.
 *
 * <p>Los importes salen como número y la moneda viaja en un campo propio,
 * porque el panel hace cuentas con estos valores.
 */
public record ResumenCajaResponse(
        LocalDate desde,
        LocalDate hasta,
        String moneda,
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
            Dinero ingresosEfectivo, Dinero egresosEfectivo,
            Dinero ingresosTransferencia, Dinero egresosTransferencia) {
        Dinero ingresos = ingresosEfectivo.mas(ingresosTransferencia);
        Dinero egresos = egresosEfectivo.mas(egresosTransferencia);
        return new ResumenCajaResponse(
                desde, hasta,
                ingresos.moneda().codigo(),
                ingresos.monto(),
                egresos.monto(),
                ingresos.menos(egresos).monto(),
                ingresosEfectivo.monto(),
                egresosEfectivo.monto(),
                ingresosEfectivo.menos(egresosEfectivo).monto(),
                ingresosTransferencia.monto(),
                egresosTransferencia.monto(),
                ingresosTransferencia.menos(egresosTransferencia).monto());
    }
}
