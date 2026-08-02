package com.techstore.compras;

import java.math.BigDecimal;
import java.util.List;

/**
 * Un proveedor candidato para la compra, con todo lo que hace falta para
 * decidir: qué cubre, a qué precio, cuánto tarda y qué tan confiable viene
 * siendo. La decisión queda en el usuario; el sistema solo ordena la
 * información.
 *
 * @param coberturaCompleta  si ofrece todos los productos pedidos
 * @param faltantes          nombres de los que no ofrece
 * @param total              lo que costaría lo que sí cubre
 * @param diasEntregaPromedio cuánto tardó en entregar, promedio del historial;
 *                            nulo si nunca se le recibió una compra
 * @param fiabilidad         porcentaje de compras cerradas que llegaron
 *                           (recibidas / (recibidas + anuladas)); nulo sin
 *                           historial. No mide calidad de la mercadería:
 *                           mide si lo que se le pidió, llegó.
 * @param comprasRecibidas   cuántas entregas respaldan estos números — un 100%
 *                           sobre 1 compra no es lo mismo que sobre 40
 */
public record ComparacionProveedorResponse(
        Long proveedorId,
        String razonSocial,
        boolean coberturaCompleta,
        List<LineaCotizada> cubre,
        List<String> faltantes,
        String moneda,
        BigDecimal total,
        Double diasEntregaPromedio,
        Double fiabilidad,
        long comprasRecibidas) {

    /** Un producto pedido, cotizado con el precio de este proveedor. */
    public record LineaCotizada(
            Long productoId,
            String nombre,
            int cantidad,
            BigDecimal precioUnitario,
            BigDecimal subtotal) {
    }
}
