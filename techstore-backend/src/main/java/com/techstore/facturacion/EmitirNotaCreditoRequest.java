package com.techstore.facturacion;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Devolución de un pedido facturado. Se indican los productos y las
 * cantidades a devolver; puede ser total (todos los items completos) o
 * parcial (algunos productos o algunas unidades).
 */
public record EmitirNotaCreditoRequest(
        @NotBlank @Size(max = 255) String motivo,
        @NotEmpty(message = "Debe indicar al menos un producto a devolver") @Valid List<ItemDevolucion> items) {

    public record ItemDevolucion(
            @NotNull Long productoId,
            @NotNull @Min(value = 1, message = "La cantidad a devolver debe ser al menos 1") Integer cantidad) {
    }
}
