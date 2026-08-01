package com.techstore.ventas;

import com.techstore.facturacion.RegistroPagoRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * Venta en mostrador: el cliente llega a la tienda sin haber hecho un pedido
 * por la web. Se arma la venta, se cobra y se emite la factura en un solo paso.
 */
public record VentaDirectaRequest(
        @NotEmpty(message = "{validacion.venta.producto.obligatorio}") @Valid List<ItemVenta> items,
        @NotEmpty(message = "{validacion.metodo.pago.obligatorio}")
        @Valid List<RegistroPagoRequest.LineaPago> lineas,
        /** Opcional: si viene, la factura sale a ese RUC en vez de a consumidor final. */
        @Valid RegistroPagoRequest.Receptor receptor,
        @Size(max = 500) String notas) {

    public record ItemVenta(
            @NotNull Long productoId,
            @NotNull @Min(value = 1, message = "{validacion.cantidad.minima}") Integer cantidad,
            /**
             * Rebaja <b>por unidad</b> sobre el precio de lista, no sobre la línea
             * entera: es como lo pide el documento electrónico. Opcional; nulo es
             * sin descuento.
             */
            @DecimalMin(value = "0", message = "{validacion.descuento.no.negativo}")
            BigDecimal descuento) {
    }
}
