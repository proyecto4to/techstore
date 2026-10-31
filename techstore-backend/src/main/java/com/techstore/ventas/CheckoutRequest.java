package com.techstore.ventas;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * Lo que manda quien confirma una compra.
 *
 * @param comprobanteIds los respaldos de la transferencia que el cliente subió
 *                       antes de confirmar (ver {@link PagoComprobanteService#subirSuelto}).
 *                       Con al menos uno, el pedido nace con el pago en verificación.
 * @param totalEsperado  el total que la pantalla le mostró y que transfirió. Si el
 *                       servidor calcula otro —cambió un precio o el envío—, el
 *                       checkout se rechaza en vez de crear un pedido que el
 *                       comprobante no cubre.
 */
public record CheckoutRequest(
        @NotEmpty @Valid List<ItemCarrito> items,
        @Size(max = 1000) String notas,
        Long direccionEntregaId,
        MetodoEntrega metodoEntrega,
        MetodoPagoCheckout metodoPago,
        Long tarifaEnvioId,
        @Size(max = PagoComprobanteService.MAXIMO_POR_PEDIDO) List<@NotNull Long> comprobanteIds,
        BigDecimal totalEsperado) {

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
        if (totalEsperado != null && totalEsperado.signum() == 0) {
            totalEsperado = null;
        }
        comprobanteIds = comprobanteIds == null ? List.of() : List.copyOf(comprobanteIds);
    }

    /** Mantiene compatibles a los clientes web anteriores a la libreta móvil. */
    public CheckoutRequest(List<ItemCarrito> items, String notas) {
        this(items, notas, null, null, null, null, null, null);
    }

    /** Compatibilidad con la firma de la Fase 5. */
    public CheckoutRequest(List<ItemCarrito> items, String notas, Long direccionEntregaId,
                           MetodoEntrega metodoEntrega, MetodoPagoCheckout metodoPago) {
        this(items, notas, direccionEntregaId, metodoEntrega, metodoPago, null, null, null);
    }

    /** Compatibilidad con los clientes anteriores al pago verificado. */
    public CheckoutRequest(List<ItemCarrito> items, String notas, Long direccionEntregaId,
                           MetodoEntrega metodoEntrega, MetodoPagoCheckout metodoPago, Long tarifaEnvioId) {
        this(items, notas, direccionEntregaId, metodoEntrega, metodoPago, tarifaEnvioId, null, null);
    }

    public record ItemCarrito(
            @NotNull Long productoId,
            @NotNull @Min(1) Integer cantidad) {
    }
}
