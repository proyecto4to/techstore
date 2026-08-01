package com.techstore.facturacion;

import com.techstore.auditoria.AuditoriaService;
import com.techstore.caja.CajaService;
import com.techstore.catalogo.Producto;
import com.techstore.catalogo.ProductoRepository;
import com.techstore.comun.AutonumeradorService;
import com.techstore.comun.BusinessException;
import com.techstore.comun.Dinero;
import com.techstore.comun.PageResponse;
import com.techstore.comun.ResourceNotFoundException;
import com.techstore.empresa.EmpresaConfigService;
import com.techstore.empresa.EmpresaProperties;
import com.techstore.usuarios.Usuario;
import com.techstore.usuarios.UsuarioRepository;
import com.techstore.ventas.EstadoPedido;
import com.techstore.ventas.Pedido;
import com.techstore.ventas.PedidoDetalle;
import com.techstore.ventas.PedidoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Devolución de un pedido facturado mediante nota de crédito. Puede ser
 * total o parcial (algunos productos o algunas unidades). En una sola
 * transacción: emite la NC con su detalle, devuelve el stock de lo
 * devuelto, registra el egreso en caja por el monto acreditado y, si se
 * devolvió todo el pedido, lo marca Devuelto y anula la factura.
 */
@Service
@RequiredArgsConstructor
public class NotaCreditoService {

    private static final String ENTIDAD_NC = "NOTACREDITO";

    private final NotaCreditoRepository notaCreditoRepository;
    private final NotaCreditoDetalleRepository notaCreditoDetalleRepository;
    private final PedidoRepository pedidoRepository;
    private final FacturaRepository facturaRepository;
    private final ProductoRepository productoRepository;
    private final UsuarioRepository usuarioRepository;
    private final AutonumeradorService autonumeradorService;
    private final CajaService cajaService;
    private final FacturaPdfService facturaPdfService;
    private final EmpresaConfigService empresaConfig;
    private final AuditoriaService auditoriaService;

    @Transactional
    public NotaCreditoResponse emitir(Long pedidoId, EmitirNotaCreditoRequest request, String emailUsuario) {
        Usuario emisor = usuarioRepository.findByEmail(emailUsuario)
                .orElseThrow(() -> new BadCredentialsException("Usuario no encontrado"));
        Pedido pedido = pedidoRepository.findWithDetallesById(pedidoId)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido", pedidoId));

        if (pedido.getEstado() != EstadoPedido.G) {
            throw new BusinessException(
                    "Solo se puede devolver un pedido Pagado (actual: %s)".formatted(pedido.getEstado()));
        }
        Factura factura = facturaRepository.findByPedidoId(pedidoId)
                .orElseThrow(() -> new BusinessException("El pedido no tiene factura emitida"));

        // Detalles del pedido indexados por producto, y lo ya devuelto en NCs previas.
        Map<Long, PedidoDetalle> detallesPedido = pedido.getDetalles().stream()
                .collect(Collectors.toMap(d -> d.getProducto().getId(), d -> d));
        Map<Long, Integer> yaDevuelto = cargarYaDevuelto(pedidoId);

        // Consolidar items del request (un producto no puede repetirse).
        Map<Long, Integer> aDevolver = new LinkedHashMap<>();
        for (EmitirNotaCreditoRequest.ItemDevolucion item : request.items()) {
            if (aDevolver.put(item.productoId(), item.cantidad()) != null) {
                throw new BusinessException(
                        "El producto %d aparece más de una vez en la devolución".formatted(item.productoId()));
            }
        }

        productoRepository.findAllForUpdate(aDevolver.keySet());

        NotaCredito nc = new NotaCredito();
        // La nota acredita una factura: va en la misma moneda que ella.
        nc.setMoneda(factura.getMoneda());
        // Mismo prefijo que las facturas: sale de los datos de la empresa.
        EmpresaProperties empresa = empresaConfig.actual();
        nc.setNumero("%s-%s-%07d".formatted(
                empresa.establecimiento(), empresa.puntoExpedicion(),
                autonumeradorService.siguiente(ENTIDAD_NC)));
        nc.setFactura(factura);
        nc.setPedido(pedido);
        nc.setMotivo(request.motivo().trim());
        nc.setUsuario(emisor);

        Dinero totalNc = Dinero.cero(nc.getMoneda());
        Dinero ivaNc = Dinero.cero(nc.getMoneda());
        for (Map.Entry<Long, Integer> entrada : aDevolver.entrySet()) {
            Long productoId = entrada.getKey();
            int cantidad = entrada.getValue();

            PedidoDetalle detallePedido = detallesPedido.get(productoId);
            if (detallePedido == null) {
                throw new BusinessException(
                        "El producto %d no pertenece al pedido N.º %d".formatted(productoId, pedido.getNumero()));
            }
            int disponible = detallePedido.getCantidad() - yaDevuelto.getOrDefault(productoId, 0);
            if (cantidad > disponible) {
                throw new BusinessException(
                        "No se puede devolver %d de '%s': quedan %d por devolver"
                                .formatted(cantidad, detallePedido.getProducto().getNombre(), disponible));
            }

            Producto producto = detallePedido.getProducto();
            producto.setStockReal((producto.getStockReal() == null ? 0 : producto.getStockReal()) + cantidad);

            Dinero subtotal = detallePedido.getPrecioUnitario().por(cantidad);
            NotaCreditoDetalle detalleNc = new NotaCreditoDetalle();
            detalleNc.setProducto(producto);
            detalleNc.setCantidad(cantidad);
            detalleNc.setIvaAplicado(detallePedido.getIvaAplicado());
            // Se agrega antes de cargarle importes: la moneda del detalle es la de la nota.
            nc.agregarDetalle(detalleNc);
            detalleNc.setPrecioUnitario(detallePedido.getPrecioUnitario());
            detalleNc.setSubtotal(subtotal);

            totalNc = totalNc.mas(subtotal);
            ivaNc = ivaNc.mas(subtotal.ivaIncluido(detallePedido.getIvaAplicado()));
        }

        nc.setTotal(totalNc);
        nc.setIva(ivaNc);
        nc.setSubtotal(totalNc.menos(ivaNc));
        nc = notaCreditoRepository.save(nc);

        // ¿El pedido quedó completamente devuelto tras esta NC?
        boolean devueltoTotal = pedido.getDetalles().stream().allMatch(dp -> {
            int devueltoAcumulado = yaDevuelto.getOrDefault(dp.getProducto().getId(), 0)
                    + aDevolver.getOrDefault(dp.getProducto().getId(), 0);
            return devueltoAcumulado >= dp.getCantidad();
        });
        if (devueltoTotal) {
            pedido.setEstado(EstadoPedido.R);
            pedidoRepository.save(pedido);
            factura.setEstado(EstadoFactura.Anulada);
            facturaRepository.save(factura);
        }

        // Mismo motivo que en el cobro: sin el prefijo, la referencia de esta
        // nota de crédito puede coincidir con la de una factura distinta.
        cajaService.registrarEgresoAutomatico(
                totalNc,
                "Devolución pedido N.º " + pedido.getNumero(),
                "NC " + nc.getNumero(),
                emisor);

        auditoriaService.registrar("NotaCredito", nc.getId(), AuditoriaService.Accion.Devolver,
                "Nota de crédito %s por %s sobre la factura %s".formatted(
                        nc.getNumero(), totalNc, factura.getNumero()),
                AuditoriaService.Cambios.nuevo()
                        .valor("notaCredito", nc.getNumero())
                        .valor("factura", factura.getNumero())
                        .valor("monto", totalNc));

        return NotaCreditoResponse.from(nc);
    }

    @Transactional(readOnly = true)
    public PageResponse<NotaCreditoResponse> listar(Pageable pageable) {
        return PageResponse.from(notaCreditoRepository.findAll(pageable).map(NotaCreditoResponse::from));
    }

    /**
     * Por cada producto del pedido, cuánto se compró, cuánto ya se devolvió en
     * NCs previas y cuánto queda disponible por devolver. El panel de devolución
     * usa esto para ofrecer y limitar solo lo que realmente falta devolver.
     */
    @Transactional(readOnly = true)
    public List<DevolucionItemResponse> disponibleParaDevolver(Long pedidoId) {
        Pedido pedido = pedidoRepository.findWithDetallesById(pedidoId)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido", pedidoId));
        Map<Long, Integer> yaDevuelto = cargarYaDevuelto(pedidoId);
        return pedido.getDetalles().stream()
                .map(detalle -> {
                    int comprado = detalle.getCantidad();
                    int devuelto = yaDevuelto.getOrDefault(detalle.getProducto().getId(), 0);
                    return new DevolucionItemResponse(
                            detalle.getProducto().getId(),
                            detalle.getProducto().getNombre(),
                            comprado,
                            devuelto,
                            comprado - devuelto,
                            detalle.getMoneda().codigo(),
                            detalle.getPrecioUnitario().monto());
                })
                .toList();
    }

    /** Número de NC + contenido PDF listos para descargar. */
    public record NotaCreditoPdf(String numero, byte[] contenido) {
    }

    @Transactional(readOnly = true)
    public NotaCreditoPdf generarPdf(Long id) {
        NotaCredito nc = notaCreditoRepository.findParaPdfById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Nota de crédito", id));
        return new NotaCreditoPdf(nc.getNumero(), facturaPdfService.generarNotaCredito(nc));
    }

    private Map<Long, Integer> cargarYaDevuelto(Long pedidoId) {
        Map<Long, Integer> mapa = new HashMap<>();
        for (Object[] fila : notaCreditoDetalleRepository.cantidadesDevueltasPorProducto(pedidoId)) {
            mapa.put((Long) fila[0], ((Number) fila[1]).intValue());
        }
        return mapa;
    }

}
