package com.techstore.service;

import com.techstore.dto.request.CompraRequest;
import com.techstore.dto.request.RegistroPagoRequest;
import com.techstore.dto.response.CompraResumenResponse;
import com.techstore.dto.response.PageResponse;
import com.techstore.entity.EstadoPedido;
import com.techstore.entity.Pedido;
import com.techstore.entity.PedidoDetalle;
import com.techstore.entity.Producto;
import com.techstore.entity.Proveedor;
import com.techstore.entity.TipoPago;
import com.techstore.entity.TipoPedido;
import com.techstore.entity.Usuario;
import com.techstore.exception.BusinessException;
import com.techstore.exception.ResourceNotFoundException;
import com.techstore.repository.PedidoRepository;
import com.techstore.repository.ProductoRepository;
import com.techstore.repository.ProveedorRepository;
import com.techstore.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
                .orElseThrow(() -> new ResourceNotFoundException("Proveedor", request.proveedorId()));
        if (!Boolean.TRUE.equals(proveedor.getActivo())) {
            throw new BusinessException("El proveedor '%s' está inactivo".formatted(proveedor.getRazonSocial()));
        }

        // Un producto repetido en la lista se rechaza: cada item lleva su costo.
        Map<Long, CompraRequest.ItemCompra> items = new LinkedHashMap<>();
        for (CompraRequest.ItemCompra item : request.items()) {
            if (items.put(item.productoId(), item) != null) {
                throw new BusinessException("El producto %d aparece más de una vez en la compra".formatted(item.productoId()));
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

        BigDecimal total = BigDecimal.ZERO;
        for (CompraRequest.ItemCompra item : items.values()) {
            Producto producto = productos.get(item.productoId());
            if (producto == null) {
                throw new ResourceNotFoundException("Producto", item.productoId());
            }
            PedidoDetalle detalle = new PedidoDetalle();
            detalle.setProducto(producto);
            detalle.setCantidad(item.cantidad());
            detalle.setPrecioUnitario(item.costoUnitario());
            detalle.setSubtotal(item.costoUnitario().multiply(BigDecimal.valueOf(item.cantidad())));
            detalle.setIvaAplicado(producto.getIva());
            pedido.agregarDetalle(detalle);
            total = total.add(detalle.getSubtotal());
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
            throw new BusinessException("Solo se puede recibir una compra Pendiente (actual: %s)"
                    .formatted(pedido.getEstado()));
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
            throw new BusinessException("No se puede pagar una compra anulada");
        }
        if (Boolean.TRUE.equals(pedido.getPagada())) {
            throw new BusinessException("La compra N.º %d ya está pagada".formatted(pedido.getNumero()));
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
                    linea.monto(),
                    "Pago a proveedor %s — Compra N.º %d (%s)"
                            .formatted(proveedor, pedido.getNumero(), linea.tipoPago()),
                    "COMPRA-" + pedido.getNumero(),
                    usuario,
                    linea.tipoPago());
        }

        return respuesta;
    }

    /** Mismas reglas que el cobro de una venta: solo efectivo/transferencia, sin repetir y sumando el total. */
    private void validarLineas(List<RegistroPagoRequest.LineaPago> lineas, BigDecimal total) {
        Set<TipoPago> vistos = new HashSet<>();
        BigDecimal suma = BigDecimal.ZERO;
        for (RegistroPagoRequest.LineaPago linea : lineas) {
            if (!METODOS_HABILITADOS.contains(linea.tipoPago())) {
                throw new BusinessException(
                        "Medio de pago no permitido: %s. Solo se aceptan Efectivo y Transferencia."
                                .formatted(linea.tipoPago()));
            }
            if (!vistos.add(linea.tipoPago())) {
                throw new BusinessException(
                        "El método %s aparece más de una vez; combiná los montos en una sola línea."
                                .formatted(linea.tipoPago()));
            }
            suma = suma.add(linea.monto());
        }
        if (suma.compareTo(total) != 0) {
            throw new BusinessException(
                    "La suma de los pagos (%s) no coincide con el total de la compra (%s)."
                            .formatted(suma.toPlainString(), total.toPlainString()));
        }
    }

    @Transactional
    public CompraResumenResponse anular(Long id) {
        Pedido pedido = buscarCompra(id);
        if (pedido.getEstado() != EstadoPedido.P) {
            throw new BusinessException("Solo se puede anular una compra Pendiente (actual: %s)"
                    .formatted(pedido.getEstado()));
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
                .orElseThrow(() -> new ResourceNotFoundException("Compra", id));
        if (pedido.getTipo() != TipoPedido.Compra) {
            throw new BusinessException("El pedido N.º %d no es una compra".formatted(pedido.getNumero()));
        }
        return pedido;
    }
}
