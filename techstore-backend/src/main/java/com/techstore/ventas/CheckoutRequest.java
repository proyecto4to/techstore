package com.techstore.ventas;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CheckoutRequest(
        @NotEmpty @Valid List<ItemCarrito> items,
        @Size(max = 1000) String notas,
        Long direccionEntregaId,
        MetodoEntrega metodoEntrega,
        MetodoPagoCheckout metodoPago,
        Long tarifaEnvioId) {

    /**
     * GeneXus serializa los identificadores numéricos opcionales vacíos como
     * cero. En la API, cero representa la ausencia del identificador porque
     * todos los IDs persistidos son positivos.
     */
    public CheckoutRequest {
        if (Long.valueOf(0L).equals(direccionEntregaId)) {
            direccionEntregaId = null;
        }
        if (Long.valueOf(0L).equals(tarifaEnvioId)) {
            tarifaEnvioId = null;
        }
    }

    /** Mantiene compatibles a los clientes web anteriores a la libreta móvil. */
    public CheckoutRequest(List<ItemCarrito> items, String notas) {
        this(items, notas, null, null, null, null);
    }

    /** Compatibilidad con la firma de la Fase 5. */
    public CheckoutRequest(List<ItemCarrito> items, String notas, Long direccionEntregaId,
                           MetodoEntrega metodoEntrega, MetodoPagoCheckout metodoPago) {
        this(items, notas, direccionEntregaId, metodoEntrega, metodoPago, null);
    }

    public record ItemCarrito(
            @NotNull Long productoId,
            @NotNull @Min(1) Integer cantidad) {
    }
}
