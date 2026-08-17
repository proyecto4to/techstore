package com.techstore.compras;

import com.techstore.comun.CondicionOperacion;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * El comprobante que mandó el proveedor, tal como lo lee alguien del papel.
 *
 * <p>Los cuatro importes son opcionales <b>en bloque</b>: si no vienen, el
 * comprobante se asienta con lo que dice la compra, que es lo normal cuando el
 * proveedor facturó exactamente lo que se le pidió. Si el papel dice otra cosa
 * —un flete, una diferencia de precio, una nota de crédito parcial— se cargan
 * los cuatro y manda el papel, porque es el papel el que se declara.
 *
 * @param tipoComprobante código de la Tabla 4 de la DNIT; 109 (factura) si no viene
 * @param gravado10       monto gravado al 10% <b>con el IVA incluido</b>
 */
public record ComprobanteCompraRequest(
        Integer tipoComprobante,
        String timbrado,
        String numero,
        @NotNull(message = "{validacion.comprobante.fecha.obligatoria}")
        LocalDate fechaEmision,
        CondicionOperacion condicion,
        @PositiveOrZero(message = "{validacion.comprobante.importe.no.negativo}")
        BigDecimal gravado10,
        @PositiveOrZero(message = "{validacion.comprobante.importe.no.negativo}")
        BigDecimal gravado5,
        @PositiveOrZero(message = "{validacion.comprobante.importe.no.negativo}")
        BigDecimal exento,
        @PositiveOrZero(message = "{validacion.comprobante.importe.no.negativo}")
        BigDecimal total,
        String asociadoNumero,
        String asociadoTimbrado) {

    /** Si el usuario cargó los importes a mano en vez de dejar los de la compra. */
    public boolean traeImportes() {
        return gravado10 != null || gravado5 != null || exento != null || total != null;
    }
}
