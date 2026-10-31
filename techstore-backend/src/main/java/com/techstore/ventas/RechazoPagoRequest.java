package com.techstore.ventas;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Por qué la tienda no valida un comprobante.
 *
 * <p>Es obligatorio porque es lo único que tiene el cliente para corregirlo:
 * «rechazado» a secas lo deja adivinando si transfirió de menos, a otra cuenta
 * o si la foto no se lee.
 */
public record RechazoPagoRequest(
        @NotBlank(message = "{validacion.pago.rechazo.motivo}") @Size(max = 300) String motivo) {
}
