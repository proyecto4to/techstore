package com.techstore.compras;

import com.techstore.auditoria.AuditoriaService;
import com.techstore.caja.CajaService;
import com.techstore.catalogo.Producto;
import com.techstore.catalogo.ProductoRepository;
import com.techstore.comun.AutonumeradorService;
import com.techstore.comun.BusinessException;
import com.techstore.comun.Dinero;
import com.techstore.comun.Moneda;
import com.techstore.comun.PageResponse;
import com.techstore.comun.ResourceNotFoundException;
import com.techstore.facturacion.RegistroPagoRequest;
import com.techstore.facturacion.TipoPago;
import com.techstore.usuarios.Usuario;
import com.techstore.usuarios.UsuarioRepository;
import com.techstore.ventas.EstadoPedido;
import com.techstore.ventas.Pedido;
import com.techstore.ventas.PedidoDetalle;
import com.techstore.ventas.PedidoRepository;
import com.techstore.ventas.TipoPedido;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Pedidos de compra a proveedores: al recibirlos, el stock real sube.
 */
@Service
@RequiredArgsConstructor
public class CompraService {

    private static final String ENTIDAD_PEDIDO = "PEDIDO";

    /** Métodos habilitados, igual que en el cobro de ventas: Tarjeta y Cheque quedan fuera. */
    private static final Set<TipoPago> METODOS_HABILITADOS =
            EnumSet.of(TipoPago.Efectivo, TipoPago.Transferencia);

    private final PedidoRepository pedidoRepository;
    private final ProductoRepository productoRepository;
    private final ProveedorRepository proveedorRepository;
    private final UsuarioRepository usuarioRepository;
    private final AutonumeradorService autonumeradorService;
    private final CajaService cajaService;
    private final AuditoriaService auditoriaService;

    @Transactional
    public CompraResumenResponse crear(CompraRequest request, String emailUsuario) {
        Usuario usuario = usuarioRepository.findByEmail(emailUsuario)
                .orElseThrow(() -> new BadCredentialsException("Usuario no encontrado"));
        Proveedor proveedor = proveedorRepository.findById(request.proveedorId())
                .orElseThrow(() -> new ResourceNotFoundException("compras.proveedor.no.encontrado", request.proveedorId()));
        if (!Boolean.TRUE.equals(proveedor.getActivo())) {
            throw new BusinessException("compras.proveedor.inactivo", proveedor.getRazonSocial());
        }

        // Un producto repetido en la lista se rechaza: cada item lleva su costo.
        Map<Long, CompraRequest.ItemCompra> items = new LinkedHashMap<>();
        for (CompraRequest.ItemCompra item : request.items()) {
            if (items.put(item.productoId(), item) != null) {
                throw new BusinessException("compras.producto.repetido", item.productoId());
            }
        }

        Map<Long, Producto> productos = productoRepository.findAllById(items.keySet()).stream()
                .collect(Collectors.toMap(Producto::getId, p -> p));

        Pedido pedido = new Pedido();
        pedido.setTipo(TipoPedido.Compra);
        pedido.setEstado(EstadoPedido.P);
        pedido.setUsuario(usuario);
        pedido.setProveedor(proveedor);
        pedido.setNotas(request.notas());
        pedido.setNumero(autonumeradorService.siguiente(ENTIDAD_PEDIDO));
        // La compra no declara moneda: se factura en la del sistema.
        pedido.setMoneda(Moneda.porDefecto());

        Dinero total = Dinero.cero(pedido.getMoneda());
        for (CompraRequest.ItemCompra item : items.values()) {
            Producto producto = productos.get(item.productoId());
            if (producto == null) {
                throw new ResourceNotFoundException("catalogo.producto.no.encontrado", item.productoId());
            }
            Dinero costoUnitario = Dinero.de(item.costoUnitario(), pedido.getMoneda());
            PedidoDetalle detalle = new PedidoDetalle();
            detalle.setProducto(producto);
            detalle.setCantidad(item.cantidad());
            detalle.setIvaAplicado(producto.getIva());
            // Se agrega antes de cargarle importes: la moneda del detalle es la del pedido.
            pedido.agregarDetalle(detalle);
            // Una compra a proveedor no lleva descuento por línea: lo que se negocia
            // con el proveedor ya viene en el costo unitario acordado.
            detalle.fijarPrecio(costoUnitario, null);
            total = total.mas(detalle.getSubtotal());
        }

        pedido.setTotal(total);
        return CompraResumenResponse.from(pedidoRepository.save(pedido));
    }

    /**
     * Recepción de la mercadería: suma el stock real de cada producto. El pago
     * al proveedor es un paso aparte (ver {@link #pagar}).
     */
    @Transactional
    public CompraResumenResponse recibir(Long id) {
        Pedido pedido = buscarCompra(id);
        if (pedido.getEstado() != EstadoPedido.P) {
            throw new BusinessException("compras.solo.recibir.pendiente", pedido.getEstado());
        }

        productoRepository.findAllForUpdate(pedido.getDetalles().stream()
                .map(detalle -> detalle.getProducto().getId())
                .collect(Collectors.toSet()));
        for (PedidoDetalle detalle : pedido.getDetalles()) {
            Producto producto = detalle.getProducto();
            int stockActual = producto.getStockReal() == null ? 0 : producto.getStockReal();
            producto.setStockReal(stockActual + detalle.getCantidad());
        }

        pedido.setEstado(EstadoPedido.C);
        return CompraResumenResponse.from(pedidoRepository.save(pedido));
    }

    /**
     * Pago de la compra al proveedor: registra el egreso en caja con el medio
     * indicado (efectivo o transferencia) y marca la compra como pagada. Es
     * independiente de la recepción: se puede pagar antes o después de recibir.
     */
    @Transactional
    public CompraResumenResponse pagar(Long id, List<RegistroPagoRequest.LineaPago> lineas, String emailUsuario) {
        Pedido pedido = buscarCompra(id);
        if (pedido.getEstado() == EstadoPedido.X) {
            throw new BusinessException("compras.no.pagar.anulada");
        }
        if (Boolean.TRUE.equals(pedido.getPagada())) {
            throw new BusinessException("compras.ya.pagada", pedido.getNumero());
        }
        validarLineas(lineas, pedido.getTotal());

        Usuario usuario = usuarioRepository.findByEmail(emailUsuario)
                .orElseThrow(() -> new BadCredentialsException("Usuario no encontrado"));

        pedido.setPagada(true);
        CompraResumenResponse respuesta = CompraResumenResponse.from(pedidoRepository.save(pedido));

        // Un egreso por medio, para que el cierre concilie el efectivo del cajón
        // aparte de lo que salió por el banco.
        String proveedor = pedido.getProveedor() != null ? pedido.getProveedor().getRazonSocial() : "proveedor";

        auditoriaService.registrar("Compra", pedido.getId(), AuditoriaService.Accion.Pagar,
                "Pagó la compra N.º %d a %s".formatted(pedido.getNumero(), proveedor),
                AuditoriaService.Cambios.nuevo()
                        .valor("compra", pedido.getNumero())
                        .valor("proveedor", proveedor)
                        .valor("total", pedido.getTotal()));

        for (RegistroPagoRequest.LineaPago linea : lineas) {
            cajaService.registrarEgresoAutomatico(
                    Dinero.de(linea.monto(), pedido.getMoneda()),
                    "Pago a proveedor %s — Compra N.º %d (%s)"
                            .formatted(proveedor, pedido.getNumero(), linea.tipoPago()),
                    "COMPRA-" + pedido.getNumero(),
                    usuario,
                    linea.tipoPago());
        }

        return respuesta;
    }

    /** Mismas reglas que el cobro de una venta: solo efectivo/transferencia, sin repetir y sumando el total. */
    private void validarLineas(List<RegistroPagoRequest.LineaPago> lineas, Dinero total) {
        Set<TipoPago> vistos = new HashSet<>();
        Dinero suma = Dinero.cero(total.moneda());
        for (RegistroPagoRequest.LineaPago linea : lineas) {
            if (!METODOS_HABILITADOS.contains(linea.tipoPago())) {
                throw new BusinessException("pago.medio.no.permitido", linea.tipoPago());
            }
            if (!vistos.add(linea.tipoPago())) {
                throw new BusinessException("pago.medio.repetido", linea.tipoPago());
            }
            suma = suma.mas(Dinero.de(linea.monto(), total.moneda()));
        }
        if (suma.compareTo(total) != 0) {
            throw new BusinessException("compras.pagos.no.suman.total", suma.plano(), total.plano());
        }
    }

    @Transactional
    public CompraResumenResponse anular(Long id) {
        Pedido pedido = buscarCompra(id);
        if (pedido.getEstado() != EstadoPedido.P) {
            throw new BusinessException("compras.solo.anular.pendiente", pedido.getEstado());
        }
        pedido.setEstado(EstadoPedido.X);
        return CompraResumenResponse.from(pedidoRepository.save(pedido));
    }

    @Transactional(readOnly = true)
    public PageResponse<CompraResumenResponse> listar(Pageable pageable) {
        return PageResponse.from(pedidoRepository.findByTipo(TipoPedido.Compra, pageable)
                .map(CompraResumenResponse::from));
    }

    private Pedido buscarCompra(Long id) {
        Pedido pedido = pedidoRepository.findWithDetallesById(id)
                .orElseThrow(() -> new ResourceNotFoundException("compras.no.encontrada", id));
        if (pedido.getTipo() != TipoPedido.Compra) {
            throw new BusinessException("compras.pedido.no.es.compra", pedido.getNumero());
        }
        return pedido;
    }
}
