package com.techstore.dto.request;

import com.techstore.entity.TipoPago;
import jakarta.validation.constraints.NotNull;

/** Pago de una compra a proveedor: el medio con el que se registra el egreso. */
public record PagoCompraRequest(
        @NotNull(message = "El medio de pago es obligatorio") TipoPago medio) {
}
