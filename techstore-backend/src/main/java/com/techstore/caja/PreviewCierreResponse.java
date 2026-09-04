package com.techstore.caja;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Vista previa del turno abierto: lo que el sistema calcula antes de que
 * el cajero cuente el efectivo físico.
 *
 * <p>Los campos nulos se omiten porque a un cajero se le devuelve la versión
 * sin arqueo: la clave no tiene que llegar siquiera con valor vacío.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PreviewCierreResponse(
        LocalDateTime desde,
        String moneda,
        BigDecimal saldoInicial,
        BigDecimal ingresosEfectivo,
        BigDecimal egresosEfectivo,
        BigDecimal ingresosTransferencia,
        BigDecimal egresosTransferencia,
        BigDecimal efectivoTeorico) {

    /**
     * La misma vista sin nada que permita adelantar el resultado del arqueo.
     *
     * <p>No alcanza con borrar {@code efectivoTeorico}: es exactamente
     * {@code saldoInicial + ingresosEfectivo - egresosEfectivo}, así que
     * dejando los movimientos el cajero lo reconstruye con una resta. Por eso
     * se va todo el bloque de totales y quedan solo el inicio del turno y la
     * moneda, que es lo que necesita para declarar cuánto tiene en el cajón.
     */
    public PreviewCierreResponse sinArqueo() {
        return new PreviewCierreResponse(desde, moneda, null, null, null, null, null, null);
    }
}
