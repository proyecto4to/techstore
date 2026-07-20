package com.techstore.dto.request;

import com.techstore.entity.TipoPago;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * Cobro de un pedido. Puede tener una o dos líneas (efectivo y/o
 * transferencia); la suma de los montos debe igualar el total del pedido.
 */
public record RegistroPagoRequest(
        @NotEmpty(message = "Debe indicar al menos un método de pago") @Valid List<LineaPago> lineas) {

    public record LineaPago(
            @NotNull TipoPago tipoPago,
            @NotNull @DecimalMin(value = "0.01", message = "El monto debe ser mayor a cero") BigDecimal monto,
            @Size(max = 50) String numeroComprobante) {
    }
}
