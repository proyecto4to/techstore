package com.techstore.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record ReporteVentasResponse(
        BigDecimal totalFacturado,
        long cantidadFacturas,
        List<EstadoCantidad> pedidosPorEstado,
        List<TopProductoResponse> topProductos) {

    public record EstadoCantidad(String estado, long cantidad) {
    }
}
