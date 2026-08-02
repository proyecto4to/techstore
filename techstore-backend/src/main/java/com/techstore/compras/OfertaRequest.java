package com.techstore.compras;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Carga manual de una oferta: sirve para registrar que un proveedor tiene un
 * producto (y a cuánto) antes de haberle comprado nunca — una cotización que
 * pasó por teléfono, una lista de precios que mandó por correo.
 */
public record OfertaRequest(
        @NotNull Long productoId,
        @NotNull @DecimalMin(value = "0.01", message = "{validacion.costo.positivo}") BigDecimal precio,
        /** Nulo = disponible. */
        Boolean disponible) {
}
