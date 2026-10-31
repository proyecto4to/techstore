package com.techstore.ventas;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record PedidoResponse(
        Long id,
        Integer numero,
        String tipo,
        String estado,
        LocalDateTime fecha,
        String moneda,
        BigDecimal total,
        BigDecimal subtotalProductos,
        BigDecimal costoEnvio,
        BigDecimal ivaEnvio,
        String notas,
        String metodoEntrega,
        String metodoPago,
        /** Verificación del pago por transferencia: NO_REQUERIDO, PENDIENTE_COMPROBANTE, EN_VERIFICACION, APROBADO o RECHAZADO. */
        String estadoPago,
        /** Por qué no se validó el último comprobante; nulo si no hubo rechazo. */
        String pagoRechazoMotivo,
        LocalDateTime pagoVerificadoEn,
        DireccionResponse direccionEntrega,
        String usuarioEmail,
        Boolean facturaDisponible,
        List<DetalleResponse> detalles) {

    public record DireccionResponse(
            Long id,
            String nombreDestinatario,
            String telefono,
            String departamento,
            String ciudad,
            String barrio,
            String direccionLinea1,
            String direccionLinea2,
            String numeroCasa,
            String referencia,
            String codigoPostal,
            BigDecimal latitud,
            BigDecimal longitud) {
    }

    public record DetalleResponse(
            Long productoId,
            String productoNombre,
            Integer cantidad,
            /** Precio de lista de una unidad, antes de cualquier rebaja. */
            BigDecimal precioUnitario,
            /**
             * Lo rebajado por unidad, sea por una promoción o por el descuento
             * que carga el mostrador. Cero cuando no hubo ninguna.
             *
             * <p>Va aparte del subtotal para que un pedido viejo se explique
             * solo: la promoción que lo justificaba puede estar apagada hoy.
             */
            BigDecimal descuento,
            BigDecimal subtotal,
            Integer ivaAplicado) {
    }
}
