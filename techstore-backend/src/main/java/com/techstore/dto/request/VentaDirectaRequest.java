package com.techstore.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Venta en mostrador: el cliente llega a la tienda sin haber hecho un pedido
 * por la web. Se arma la venta, se cobra y se emite la factura en un solo paso.
 */
public record VentaDirectaRequest(
        @NotEmpty(message = "Agregá al menos un producto") @Valid List<ItemVenta> items,
        @NotEmpty(message = "Debe indicar al menos un método de pago")
        @Valid List<RegistroPagoRequest.LineaPago> lineas,
        /** Opcional: si viene, la factura sale a ese RUC en vez de a consumidor final. */
        @Valid RegistroPagoRequest.Receptor receptor,
        @Size(max = 500) String notas) {

    public record ItemVenta(
            @NotNull Long productoId,
            @NotNull @Min(value = 1, message = "La cantidad debe ser al menos 1") Integer cantidad) {
    }
}
