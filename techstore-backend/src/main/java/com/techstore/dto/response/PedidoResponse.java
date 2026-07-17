package com.techstore.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record PedidoResponse(
        Long id,
        Integer numero,
        String tipo,
        String estado,
        LocalDateTime fecha,
        BigDecimal total,
        String notas,
        String usuarioEmail,
        List<DetalleResponse> detalles) {

    public record DetalleResponse(
            Long productoId,
            String productoNombre,
            Integer cantidad,
            BigDecimal precioUnitario,
            BigDecimal subtotal,
            Integer ivaAplicado) {
    }
}
