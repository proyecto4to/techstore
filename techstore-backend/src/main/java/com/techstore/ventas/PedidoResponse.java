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
            BigDecimal precioUnitario,
            BigDecimal subtotal,
            Integer ivaAplicado) {
    }
}
