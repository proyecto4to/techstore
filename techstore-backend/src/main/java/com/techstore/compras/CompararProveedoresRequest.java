package com.techstore.compras;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Lo que se quiere comprar, para ver qué proveedor conviene: productos y
 * cantidades, nada más. El proveedor lo elige el usuario después de comparar.
 */
public record CompararProveedoresRequest(
        @NotEmpty(message = "{validacion.venta.producto.obligatorio}") @Valid List<Item> items) {

    public record Item(
            @NotNull Long productoId,
            @NotNull @Min(value = 1, message = "{validacion.cantidad.minima}") Integer cantidad) {
    }
}
