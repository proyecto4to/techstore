package com.techstore.reportes;

import com.techstore.caja.CierreCajaService;
import com.techstore.caja.PreviewCierreResponse;
import com.techstore.facturacion.EstadoFactura;
import com.techstore.facturacion.FacturaRepository;
import com.techstore.ventas.EstadoPedido;
import com.techstore.ventas.PedidoRepository;
import com.techstore.ventas.TipoPedido;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final int UMBRAL_STOCK = 5;
    private static final int TOP_STOCK = 5;
    private static final LocalDateTime ORIGEN = LocalDateTime.of(2000, 1, 1, 0, 0);

    private final FacturaRepository facturaRepository;
    private final PedidoRepository pedidoRepository;
    private final ReporteService reporteService;
    private final CierreCajaService cierreCajaService;

    @Transactional(readOnly = true)
    public DashboardResponse resumen() {
        LocalDateTime inicioHoy = LocalDate.now().atStartOfDay();
        LocalDateTime finHoy = inicioHoy.plusDays(1);

        BigDecimal ventasHoy = facturaRepository.totalPorEstadoEntre(EstadoFactura.Pagada, inicioHoy, finHoy);
        long facturasHoy = facturaRepository
                .countByEstadoAndFechaGreaterThanEqualAndFechaLessThan(EstadoFactura.Pagada, inicioHoy, finHoy);

        // Pedidos de venta abiertos, por estado (todo el historial).
        Map<EstadoPedido, Long> porEstado = pedidoRepository
                .contarPorEstadoEntre(TipoPedido.Venta, ORIGEN, LocalDateTime.now()).stream()
                .collect(Collectors.toMap(f -> (EstadoPedido) f[0], f -> (Long) f[1]));
        long pedidosPendientes = porEstado.getOrDefault(EstadoPedido.P, 0L);
        long pedidosPorCobrar = porEstado.getOrDefault(EstadoPedido.C, 0L);

        List<StockBajoResponse> stockBajo = reporteService.stockBajo(UMBRAL_STOCK, TOP_STOCK);
        long stockBajoCount = reporteService.stockBajo(UMBRAL_STOCK, 1000).size();

        PreviewCierreResponse turno = cierreCajaService.preview();

        return new DashboardResponse(
                ventasHoy, facturasHoy, pedidosPendientes, pedidosPorCobrar,
                stockBajoCount, stockBajo, turno.efectivoTeorico(), turno.ingresosTransferencia());
    }
}
