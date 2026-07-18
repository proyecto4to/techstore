package com.techstore.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ReporteVentasResponse(
        LocalDate desde,
        LocalDate hasta,
        BigDecimal totalFacturado,
        long cantidadFacturas,
        List<EstadoCantidad> pedidosPorEstado,
        List<VentaDiaria> ventasPorDia,
        List<TopProductoResponse> topProductos,
        List<TopCliente> topClientes) {

    public record EstadoCantidad(String estado, long cantidad) {
    }

    public record VentaDiaria(LocalDate fecha, BigDecimal total, long cantidadFacturas) {
    }

    public record TopCliente(String email, String nombre, long pedidos, BigDecimal monto) {
    }
}
