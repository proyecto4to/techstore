package com.techstore.service;

import com.techstore.dto.response.ReporteVentasResponse;
import com.techstore.dto.response.TopProductoResponse;
import com.techstore.entity.EstadoFactura;
import com.techstore.entity.EstadoPedido;
import com.techstore.repository.FacturaRepository;
import com.techstore.repository.PedidoDetalleRepository;
import com.techstore.repository.PedidoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReporteServiceTest {

    @Mock
    private FacturaRepository facturaRepository;

    @Mock
    private PedidoRepository pedidoRepository;

    @Mock
    private PedidoDetalleRepository pedidoDetalleRepository;

    private ReporteService reporteService;

    @BeforeEach
    void setUp() {
        reporteService = new ReporteService(facturaRepository, pedidoRepository, pedidoDetalleRepository);
    }

    @Test
    void ventasArmaElReporteCompleto() {
        when(facturaRepository.totalPorEstado(EstadoFactura.Pagada)).thenReturn(new BigDecimal("4700000.00"));
        when(facturaRepository.countByEstado(EstadoFactura.Pagada)).thenReturn(1L);
        when(pedidoRepository.contarPorEstado()).thenReturn(List.of(
                new Object[]{EstadoPedido.G, 1L},
                new Object[]{EstadoPedido.X, 1L}));
        List<TopProductoResponse> top = List.of(
                new TopProductoResponse(3L, "Samsung 1TB SSD", 2L, new BigDecimal("1200000.00")),
                new TopProductoResponse(2L, "NVIDIA RTX 4090", 1L, new BigDecimal("3500000.00")));
        when(pedidoDetalleRepository.topProductos(anyCollection(), eq(PageRequest.of(0, 5)))).thenReturn(top);

        ReporteVentasResponse reporte = reporteService.ventas(5);

        assertEquals(new BigDecimal("4700000.00"), reporte.totalFacturado());
        assertEquals(1L, reporte.cantidadFacturas());
        assertEquals(2, reporte.pedidosPorEstado().size());
        assertEquals("G", reporte.pedidosPorEstado().get(0).estado());
        assertEquals(top, reporte.topProductos());
        verify(pedidoDetalleRepository).topProductos(
                eq(List.of(EstadoPedido.C, EstadoPedido.G, EstadoPedido.N)), eq(PageRequest.of(0, 5)));
    }

    @Test
    void sinVentasDevuelveCeros() {
        when(facturaRepository.totalPorEstado(EstadoFactura.Pagada)).thenReturn(BigDecimal.ZERO);
        when(facturaRepository.countByEstado(EstadoFactura.Pagada)).thenReturn(0L);
        when(pedidoRepository.contarPorEstado()).thenReturn(List.of());
        when(pedidoDetalleRepository.topProductos(anyCollection(), eq(PageRequest.of(0, 3)))).thenReturn(List.of());

        ReporteVentasResponse reporte = reporteService.ventas(3);

        assertEquals(BigDecimal.ZERO, reporte.totalFacturado());
        assertEquals(0L, reporte.cantidadFacturas());
        assertEquals(0, reporte.pedidosPorEstado().size());
        assertEquals(0, reporte.topProductos().size());
    }
}
