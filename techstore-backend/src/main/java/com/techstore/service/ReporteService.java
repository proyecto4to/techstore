package com.techstore.service;

import com.techstore.dto.response.ReporteVentasResponse;
import com.techstore.dto.response.TopProductoResponse;
import com.techstore.entity.EstadoFactura;
import com.techstore.entity.EstadoPedido;
import com.techstore.repository.FacturaRepository;
import com.techstore.repository.PedidoDetalleRepository;
import com.techstore.repository.PedidoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReporteService {

    /** Estados que cuentan como venta concretada o en curso (excluye pendientes y anulados). */
    private static final List<EstadoPedido> ESTADOS_VENDIDOS =
            List.of(EstadoPedido.C, EstadoPedido.G, EstadoPedido.N);

    private final FacturaRepository facturaRepository;
    private final PedidoRepository pedidoRepository;
    private final PedidoDetalleRepository pedidoDetalleRepository;

    @Transactional(readOnly = true)
    public ReporteVentasResponse ventas(int topN) {
        BigDecimal totalFacturado = facturaRepository.totalPorEstado(EstadoFactura.Pagada);
        long cantidadFacturas = facturaRepository.countByEstado(EstadoFactura.Pagada);

        List<ReporteVentasResponse.EstadoCantidad> porEstado = pedidoRepository.contarPorEstado().stream()
                .map(fila -> new ReporteVentasResponse.EstadoCantidad(
                        ((EstadoPedido) fila[0]).name(), (Long) fila[1]))
                .toList();

        List<TopProductoResponse> top =
                pedidoDetalleRepository.topProductos(ESTADOS_VENDIDOS, PageRequest.of(0, topN));

        return new ReporteVentasResponse(totalFacturado, cantidadFacturas, porEstado, top);
    }
}
