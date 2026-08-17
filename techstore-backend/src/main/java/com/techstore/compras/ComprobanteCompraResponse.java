package com.techstore.compras;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * El comprobante del proveedor tal como lo muestra el panel. El IVA viaja
 * calculado porque es lo que se va a acreditar, y no está guardado en ningún
 * lado: sale de los montos gravados.
 */
public record ComprobanteCompraResponse(
        Long id,
        Integer compraNumero,
        int tipoComprobante,
        String tipoComprobanteEtiqueta,
        String timbrado,
        String numero,
        LocalDate fechaEmision,
        String condicion,
        String proveedorRuc,
        String proveedorRazonSocial,
        String moneda,
        BigDecimal gravado10,
        BigDecimal gravado5,
        BigDecimal exento,
        BigDecimal total,
        BigDecimal iva10,
        BigDecimal iva5,
        BigDecimal creditoFiscal,
        String asociadoNumero,
        String asociadoTimbrado) {

    public static ComprobanteCompraResponse from(CompraComprobante c) {
        return new ComprobanteCompraResponse(
                c.getId(),
                c.getPedido().getNumero(),
                c.getTipoComprobante().codigo(),
                c.getTipoComprobante().etiqueta(),
                c.getTimbrado(),
                c.getNumero(),
                c.getFechaEmision(),
                c.getCondicion().etiqueta(),
                c.getProveedorRuc(),
                c.getProveedorRazonSocial(),
                c.getMoneda().codigo(),
                c.getGravado10().monto(),
                c.getGravado5().monto(),
                c.getExento().monto(),
                c.getTotal().monto(),
                c.getIva10().monto(),
                c.getIva5().monto(),
                c.getCreditoFiscal().monto(),
                c.getAsociadoNumero(),
                c.getAsociadoTimbrado());
    }
}
