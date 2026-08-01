package com.techstore.facturacion;

import java.math.BigDecimal;

/**
 * Estado de devolución de un producto dentro de un pedido: cuánto se compró,
 * cuánto ya se devolvió en notas de crédito previas y cuánto queda disponible
 * por devolver. Alimenta el panel de devolución para que solo se ofrezca lo
 * que realmente falta devolver.
 */
public record DevolucionItemResponse(
        Long productoId,
        String productoNombre,
        int comprado,
        int devuelto,
        int disponible,
        String moneda,
        BigDecimal precioUnitario) {
}
