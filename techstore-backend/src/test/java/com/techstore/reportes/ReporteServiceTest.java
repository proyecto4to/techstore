package com.techstore.reportes;

import com.techstore.catalogo.Marca;
import com.techstore.catalogo.Producto;
import com.techstore.catalogo.ProductoRepository;
import com.techstore.facturacion.EstadoFactura;
import com.techstore.facturacion.FacturaRepository;
import com.techstore.ventas.EstadoPedido;
import com.techstore.ventas.PedidoDetalleRepository;
import com.techstore.ventas.PedidoRepository;
import com.techstore.ventas.TipoPedido;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
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

    @Mock
    private ProductoRepository productoRepository;

    @Mock
    private ReportePdfService reportePdfService;

    private ReporteService reporteService;

    @BeforeEach
    void setUp() {
        reporteService = new ReporteService(
                facturaRepository, pedidoRepository, pedidoDetalleRepository, productoRepository, reportePdfService);
    }

    @Test
    void ventasConRangoArmaElReporteCompleto() {
        LocalDate desde = LocalDate.of(2026, 7, 1);
        LocalDate hasta = LocalDate.of(2026, 7, 17);
        LocalDateTime inicio = desde.atStartOfDay();
        LocalDateTime fin = hasta.plusDays(1).atStartOfDay();

        when(facturaRepository.totalPorEstadoEntre(EstadoFactura.Pagada, inicio, fin))
                .thenReturn(new BigDecimal("4700000.00"));
        when(facturaRepository.countByEstadoAndFechaGreaterThanEqualAndFechaLessThan(
                EstadoFactura.Pagada, inicio, fin)).thenReturn(1L);
        when(pedidoRepository.contarPorEstadoEntre(TipoPedido.Venta, inicio, fin)).thenReturn(List.of(
                new Object[]{EstadoPedido.G, 1L},
                new Object[]{EstadoPedido.X, 1L}));
        when(facturaRepository.ventasPorDia(EstadoFactura.Pagada, inicio, fin)).thenReturn(List.<Object[]>of(
                new Object[]{Date.valueOf("2026-07-16"), new BigDecimal("4700000.00"), 1L}));
        List<TopProductoResponse> top = List.of(
                new TopProductoResponse(3L, "Samsung 1TB SSD", 2L, new BigDecimal("1200000.00")));
        when(pedidoDetalleRepository.topProductos(eq(TipoPedido.Venta), anyCollection(), eq(inicio), eq(fin), any()))
                .thenReturn(top);
        when(pedidoRepository.topClientes(eq(TipoPedido.Venta), anyCollection(), eq(inicio), eq(fin), any()))
                .thenReturn(List.<Object[]>of(
                        new Object[]{"admin@techstore.py", "Admin Sistema", 2L, new BigDecimal("7200000.00")}));

        ReporteVentasResponse reporte = reporteService.ventas(desde, hasta, 5);

        assertEquals(desde, reporte.desde());
        assertEquals(hasta, reporte.hasta());
        assertEquals(new BigDecimal("4700000.00"), reporte.totalFacturado());
        assertEquals(1L, reporte.cantidadFacturas());
        assertEquals(2, reporte.pedidosPorEstado().size());
        assertEquals(1, reporte.ventasPorDia().size());
        assertEquals(LocalDate.of(2026, 7, 16), reporte.ventasPorDia().get(0).fecha());
        assertEquals(top, reporte.topProductos());
        assertEquals(1, reporte.topClientes().size());
        assertEquals("admin@techstore.py", reporte.topClientes().get(0).email());
        verify(pedidoDetalleRepository).topProductos(
                eq(TipoPedido.Venta),
                eq(List.of(EstadoPedido.C, EstadoPedido.G, EstadoPedido.N)),
                eq(inicio), eq(fin), eq(PageRequest.of(0, 5)));
    }

    @Test
    void sinFechasUsaTodoElHistorial() {
        when(facturaRepository.totalPorEstadoEntre(eq(EstadoFactura.Pagada), any(), any()))
                .thenReturn(BigDecimal.ZERO);
        when(facturaRepository.countByEstadoAndFechaGreaterThanEqualAndFechaLessThan(
                eq(EstadoFactura.Pagada), any(), any())).thenReturn(0L);
        when(pedidoRepository.contarPorEstadoEntre(any(), any(), any())).thenReturn(List.of());
        when(facturaRepository.ventasPorDia(eq(EstadoFactura.Pagada), any(), any())).thenReturn(List.of());
        when(pedidoDetalleRepository.topProductos(any(), anyCollection(), any(), any(), any())).thenReturn(List.of());
        when(pedidoRepository.topClientes(any(), anyCollection(), any(), any(), any())).thenReturn(List.of());

        ReporteVentasResponse reporte = reporteService.ventas(null, null, 3);

        assertEquals(LocalDate.of(2000, 1, 1), reporte.desde());
        assertEquals(LocalDate.now(), reporte.hasta());
        assertEquals(BigDecimal.ZERO, reporte.totalFacturado());
    }

    @Test
    void stockBajoDevuelveProductosMapeados() {
        Marca marca = new Marca();
        marca.setNombre("NVIDIA");
        Producto gpu = new Producto();
        gpu.setId(2L);
        gpu.setNombre("NVIDIA RTX 4090");
        gpu.setMarca(marca);
        gpu.setStockReal(1);
        gpu.setStockComprometido(0);
        // El umbral que se pasa ahora es el general: se aplica solo a los
        // productos que no tienen un mínimo propio, y el límite lo recorta el
        // servicio en vez de la consulta.
        when(productoRepository.necesitanReposicion(5)).thenReturn(List.of(gpu));

        List<StockBajoResponse> resultado = reporteService.stockBajo(5, 20);

        assertEquals(1, resultado.size());
        assertEquals("NVIDIA RTX 4090", resultado.get(0).nombre());
        assertEquals("NVIDIA", resultado.get(0).marca());
        assertEquals(1, resultado.get(0).stockDisponible());
        assertEquals(Producto.UMBRAL_POR_DEFECTO, resultado.get(0).umbral());
    }
}
