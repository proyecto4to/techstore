package com.techstore.service;

import com.techstore.dto.response.ReporteVentasResponse;
import com.techstore.dto.response.StockBajoResponse;
import com.techstore.dto.response.TopProductoResponse;
import com.techstore.entity.EstadoFactura;
import com.techstore.entity.EstadoPedido;
import com.techstore.entity.TipoPedido;
import com.techstore.repository.FacturaRepository;
import com.techstore.repository.PedidoDetalleRepository;
import com.techstore.repository.PedidoRepository;
import com.techstore.repository.ProductoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReporteService {

    /** Estados que cuentan como venta concretada o en curso (excluye pendientes y anulados). */
    private static final List<EstadoPedido> ESTADOS_VENDIDOS =
            List.of(EstadoPedido.C, EstadoPedido.G, EstadoPedido.N);

    /** Inicio del historial cuando no se indica fecha "desde". */
    private static final LocalDate ORIGEN = LocalDate.of(2000, 1, 1);

    private final FacturaRepository facturaRepository;
    private final PedidoRepository pedidoRepository;
    private final PedidoDetalleRepository pedidoDetalleRepository;
    private final ProductoRepository productoRepository;
    private final ReportePdfService reportePdfService;

    @Transactional(readOnly = true)
    public ReporteVentasResponse ventas(LocalDate desde, LocalDate hasta, int topN) {
        LocalDate desdeEfectivo = desde != null ? desde : ORIGEN;
        LocalDate hastaEfectivo = hasta != null ? hasta : LocalDate.now();
        LocalDateTime inicio = desdeEfectivo.atStartOfDay();
        LocalDateTime fin = hastaEfectivo.plusDays(1).atStartOfDay(); // rango inclusivo

        BigDecimal totalFacturado =
                facturaRepository.totalPorEstadoEntre(EstadoFactura.Pagada, inicio, fin);
        long cantidadFacturas = facturaRepository
                .countByEstadoAndFechaGreaterThanEqualAndFechaLessThan(EstadoFactura.Pagada, inicio, fin);

        List<ReporteVentasResponse.EstadoCantidad> porEstado =
                pedidoRepository.contarPorEstadoEntre(TipoPedido.Venta, inicio, fin).stream()
                        .map(fila -> new ReporteVentasResponse.EstadoCantidad(
                                ((EstadoPedido) fila[0]).name(), (Long) fila[1]))
                        .toList();

        List<ReporteVentasResponse.VentaDiaria> porDia =
                facturaRepository.ventasPorDia(EstadoFactura.Pagada, inicio, fin).stream()
                        .map(fila -> new ReporteVentasResponse.VentaDiaria(
                                aLocalDate(fila[0]), (BigDecimal) fila[1], (Long) fila[2]))
                        .toList();

        List<TopProductoResponse> topProductos = pedidoDetalleRepository
                .topProductos(TipoPedido.Venta, ESTADOS_VENDIDOS, inicio, fin, PageRequest.of(0, topN));

        List<ReporteVentasResponse.TopCliente> topClientes =
                pedidoRepository.topClientes(TipoPedido.Venta, ESTADOS_VENDIDOS, inicio, fin, PageRequest.of(0, topN)).stream()
                        .map(fila -> new ReporteVentasResponse.TopCliente(
                                (String) fila[0], (String) fila[1], (Long) fila[2], (BigDecimal) fila[3]))
                        .toList();

        return new ReporteVentasResponse(desdeEfectivo, hastaEfectivo,
                totalFacturado, cantidadFacturas, porEstado, porDia, topProductos, topClientes);
    }

    @Transactional(readOnly = true)
    public List<StockBajoResponse> stockBajo(int umbral, int limite) {
        return productoRepository.conStockBajo(umbral, PageRequest.of(0, limite)).stream()
                .map(StockBajoResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public byte[] ventasPdf(LocalDate desde, LocalDate hasta, int topN) {
        return reportePdfService.ventas(ventas(desde, hasta, topN));
    }

    @Transactional(readOnly = true)
    public byte[] stockBajoPdf(int umbral, int limite) {
        return reportePdfService.stockBajo(stockBajo(umbral, limite), umbral);
    }

    private static LocalDate aLocalDate(Object valor) {
        if (valor instanceof LocalDate fecha) {
            return fecha;
        }
        if (valor instanceof Date fecha) {
            return fecha.toLocalDate();
        }
        throw new IllegalStateException("Tipo de fecha inesperado: " + valor.getClass());
    }
}
