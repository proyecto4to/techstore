package com.techstore.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * Pago de una compra a proveedor. Puede repartirse entre efectivo y
 * transferencia (pago mixto); la suma de las líneas debe igualar el total de la
 * compra. Cada línea genera su propio egreso en caja, para que el arqueo
 * distinga lo que salió del cajón de lo que salió del banco.
 */
public record PagoCompraRequest(
        @NotEmpty(message = "Debe indicar al menos un método de pago")
        @Valid List<RegistroPagoRequest.LineaPago> lineas) {
}
