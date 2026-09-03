package com.techstore.catalogo;

import java.math.BigDecimal;

/**
 * Contrato operativo: lo que necesita ver quien atiende el mostrador o carga
 * una compra al proveedor.
 *
 * <p>Existe porque los dos contratos que había no servían para esto. El público
 * no trae inventario y techa la cantidad en 99, y una venta de mostrador puede
 * ser por más unidades. El de administración sí trae el inventario, pero además
 * el costo y el margen de cada producto, y para cobrar en la caja o pedirle
 * mercadería a un proveedor eso no hace falta: dárselo a todo el que atiende
 * publicaría la rentabilidad del negocio dentro del local.
 *
 * <p>Así que acá va el inventario de verdad, y ni costo, ni margen, ni mínimos,
 * ni flags operativos, ni timestamps internos.
 */
public record ProductoOperativoResponse(
        Long id,
        String nombre,
        String moneda,
        BigDecimal precio,
        Integer iva,
        /** Existencias físicas, incluido lo ya comprometido. */
        Integer stockReal,
        /** Existencias menos lo comprometido por pedidos pendientes. */
        Integer stockDisponible,
        ProductoResponse.MarcaRef marca,
        ProductoResponse.ModeloRef modelo,
        String imagenUrl) {
}
