package com.techstore.ventas;

import com.techstore.catalogo.Producto;
import com.techstore.catalogo.ProductoRepository;
import com.techstore.comun.AutonumeradorService;
import com.techstore.comun.BusinessException;
import com.techstore.comun.Dinero;
import com.techstore.comun.Moneda;
import com.techstore.comun.PageResponse;
import com.techstore.comun.ResourceNotFoundException;
import com.techstore.direcciones.DireccionEntrega;
import com.techstore.direcciones.DireccionEntregaService;
import com.techstore.envios.CotizacionEnvio;
import com.techstore.envios.EnvioService;
import com.techstore.notificaciones.outbox.NotificationOutboxPublisher;
import com.techstore.usuarios.Usuario;
import com.techstore.usuarios.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Service
@RequiredArgsConstructor
public class PedidoService {

    private static final String ENTIDAD_PEDIDO = "PEDIDO";

    private final PedidoRepository pedidoRepository;
    private final ProductoRepository productoRepository;
    private final UsuarioRepository usuarioRepository;
    private final AutonumeradorService autonumeradorService;
    private final PedidoMapper pedidoMapper;
    private final NotificationOutboxPublisher notificationPublisher;
    private final DireccionEntregaService direccionEntregaService;
    private final EnvioService envioService;

    /**
     * Crea un pedido de venta en estado Pendiente comprometiendo el stock.
     * Las filas de productos se bloquean durante la transacción para que dos
     * checkouts simultáneos no puedan sobrevender el mismo stock.
     */
    @Transactional
    public PedidoResponse checkout(String emailUsuario, CheckoutRequest request) {
        return checkout(emailUsuario, request, null);
    }

    /**
     * Ejecuta un checkout idempotente cuando llega una clave. El bloqueo de la
     * fila del usuario hace que dos solicitudes concurrentes con la misma clave
     * se procesen en serie, antes de tocar stock.
     */
    @Transactional
    public PedidoResponse checkout(String emailUsuario, CheckoutRequest request, String idempotencyKey) {
        String clave = normalizarClave(idempotencyKey);
        Usuario usuario = (clave == null
                ? usuarioRepository.findByEmail(emailUsuario)
                : usuarioRepository.findByEmailForUpdate(emailUsuario))
                .orElseThrow(() -> new BadCredentialsException("Usuario no encontrado"));

        // Fusiona items repetidos del mismo producto (la BD exige unicidad pedido+producto).
        Map<Long, Integer> cantidades = new LinkedHashMap<>();
        for (CheckoutRequest.ItemCarrito item : request.items()) {
            cantidades.merge(item.productoId(), item.cantidad(), Integer::sum);
        }

        MetodoEntrega metodoEntrega = request.metodoEntrega() == null
                ? MetodoEntrega.RETIRO_TIENDA
                : request.metodoEntrega();
        MetodoPagoCheckout metodoPago = request.metodoPago() == null
                ? MetodoPagoCheckout.PAGO_EN_LOCAL
                : request.metodoPago();
        String hash = clave == null ? null : hashSolicitud(request, cantidades, metodoEntrega, metodoPago);

        if (clave != null) {
            var existente = pedidoRepository.findWithDetallesByUsuarioIdAndIdempotencyKey(usuario.getId(), clave);
            if (existente.isPresent()) {
                Pedido pedidoExistente = existente.get();
                if (!hash.equals(pedidoExistente.getIdempotencyRequestHash())) {
                    throw new BusinessException("ventas.checkout.idempotencia.conflicto");
                }
                return pedidoMapper.toResponse(pedidoExistente);
            }
        }

        DireccionEntrega direccion = request.direccionEntregaId() == null
                ? null
                : direccionEntregaService.buscarPropia(usuario, request.direccionEntregaId());
        if (metodoEntrega == MetodoEntrega.ENVIO_DOMICILIO && direccion == null) {
            throw new BusinessException("ventas.checkout.direccion.requerida");
        }
        validarMedioPago(metodoEntrega, metodoPago);

        Map<Long, Producto> productos = productoRepository.findAllForUpdate(cantidades.keySet()).stream()
                .collect(LinkedHashMap::new, (map, p) -> map.put(p.getId(), p), Map::putAll);

        Pedido pedido = new Pedido();
        pedido.setTipo(TipoPedido.Venta);
        pedido.setEstado(EstadoPedido.P);
        pedido.setUsuario(usuario);
        pedido.setNotas(request.notas());
        pedido.setDireccionEntrega(DireccionPedidoSnapshot.desde(direccion));
        pedido.setMetodoEntrega(metodoEntrega);
        pedido.setMetodoPago(metodoPago);
        pedido.setIdempotencyKey(clave);
        pedido.setIdempotencyRequestHash(hash);
        pedido.setNumero(autonumeradorService.siguiente(ENTIDAD_PEDIDO));
        // La moneda del pedido es la del catálogo; los detalles heredan la suya de acá.
        pedido.setMoneda(Moneda.porDefecto());

        Dinero total = Dinero.cero(pedido.getMoneda());
        for (Map.Entry<Long, Integer> entrada : cantidades.entrySet()) {
            Producto producto = productos.get(entrada.getKey());
            if (producto == null) {
                throw new ResourceNotFoundException("catalogo.producto.no.encontrado", entrada.getKey());
            }
            if (!Boolean.TRUE.equals(producto.getActivo())) {
                throw new BusinessException("catalogo.producto.no.disponible", producto.getNombre());
            }
            int cantidad = entrada.getValue();
            if (cantidad > producto.getStockDisponible()) {
                throw new BusinessException("catalogo.stock.insuficiente", producto.getNombre(), producto.getStockDisponible(), cantidad);
            }

            producto.setStockComprometido(nvl(producto.getStockComprometido()) + cantidad);

            PedidoDetalle detalle = new PedidoDetalle();
            detalle.setProducto(producto);
            detalle.setCantidad(cantidad);
            detalle.setIvaAplicado(producto.getIva());
            // Se agrega antes de cargarle importes: la moneda del detalle es la del pedido.
            pedido.agregarDetalle(detalle);
            // Una compra por la web va sin descuento: el cliente no se rebaja el precio
            // a sí mismo. El descuento se carga en el mostrador (venta directa).
            detalle.fijarPrecio(producto.getPrecio(), null);

            total = total.mas(detalle.getSubtotal());
        }

        Map<Producto, Integer> itemsEnvio = new LinkedHashMap<>();
        cantidades.forEach((id, cantidad) -> itemsEnvio.put(productos.get(id), cantidad));
        CotizacionEnvio cotizacion = envioService.seleccionar(
                direccion, itemsEnvio, total, request.tarifaEnvioId(), metodoEntrega);
        pedido.setCostoEnvio(cotizacion.costo());
        pedido.setIvaEnvio(cotizacion.iva());
        pedido.setTotal(total.mas(cotizacion.costo()));
        Pedido guardado = pedidoRepository.save(pedido);
        envioService.registrar(guardado, direccion, cotizacion);
        notificationPublisher.pedidoCreado(guardado);
        return pedidoMapper.toResponse(guardado);
    }

    private void validarMedioPago(MetodoEntrega entrega, MetodoPagoCheckout pago) {
        if (entrega == MetodoEntrega.ENVIO_DOMICILIO
                && pago == MetodoPagoCheckout.PAGO_EN_LOCAL) {
            throw new BusinessException("ventas.checkout.pago.local.incompatible");
        }
        if (entrega == MetodoEntrega.RETIRO_TIENDA
                && pago == MetodoPagoCheckout.PAGO_EFECTIVO_REPARTIDOR) {
            throw new BusinessException("ventas.checkout.pago.repartidor.incompatible");
        }
    }

    private String normalizarClave(String idempotencyKey) {
        if (idempotencyKey == null) {
            return null;
        }
        String clave = idempotencyKey.trim();
        if (clave.isEmpty() || clave.length() > 100) {
            throw new BusinessException("ventas.checkout.idempotencia.invalida");
        }
        return clave;
    }

    private String hashSolicitud(CheckoutRequest request,
                                 Map<Long, Integer> cantidades,
                                 MetodoEntrega metodoEntrega,
                                 MetodoPagoCheckout metodoPago) {
        StringBuilder canonica = new StringBuilder();
        new TreeMap<>(cantidades).forEach((productoId, cantidad) ->
                canonica.append(productoId).append(':').append(cantidad).append(';'));
        canonica.append('|').append(request.notas() == null ? "" : request.notas());
        canonica.append('|').append(request.direccionEntregaId() == null ? "" : request.direccionEntregaId());
        canonica.append('|').append(metodoEntrega.name());
        canonica.append('|').append(metodoPago.name());
        canonica.append('|').append(request.tarifaEnvioId() == null ? "" : request.tarifaEnvioId());
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonica.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 no está disponible", ex);
        }
    }

    /**
     * Confirma un pedido pendiente: descuenta el stock real y libera el comprometido.
     */
    @Transactional
    public PedidoResponse confirmar(Long id) {
        Pedido pedido = buscarConDetalles(id);
        exigirEstado(pedido, EstadoPedido.P, "ventas.pedido.no.confirmable");

        bloquearProductosDe(pedido);
        for (PedidoDetalle detalle : pedido.getDetalles()) {
            Producto producto = detalle.getProducto();
            int nuevoStock = nvl(producto.getStockReal()) - detalle.getCantidad();
            if (nuevoStock < 0) {
                throw new BusinessException("catalogo.stock.real.negativo", producto.getNombre());
            }
            producto.setStockReal(nuevoStock);
            producto.setStockComprometido(Math.max(0, nvl(producto.getStockComprometido()) - detalle.getCantidad()));
        }

        pedido.setEstado(EstadoPedido.C);
        envioService.pedidoConfirmado(pedido);
        Pedido guardado = pedidoRepository.save(pedido);
        notificationPublisher.pedidoConfirmado(guardado);
        return pedidoMapper.toResponse(guardado);
    }

    /**
     * Anula un pedido pendiente liberando el stock comprometido.
     * El dueño del pedido puede anular el suyo; un Admin puede anular cualquiera.
     */
    @Transactional
    public PedidoResponse cancelar(Long id, String emailSolicitante, boolean esAdmin) {
        Pedido pedido = buscarConDetalles(id);
        if (!esAdmin && !pedido.getUsuario().getEmail().equals(emailSolicitante)) {
            throw new AccessDeniedException("No puede anular pedidos de otro usuario");
        }
        exigirEstado(pedido, EstadoPedido.P, "ventas.pedido.no.anulable");

        bloquearProductosDe(pedido);
        for (PedidoDetalle detalle : pedido.getDetalles()) {
            Producto producto = detalle.getProducto();
            producto.setStockComprometido(Math.max(0, nvl(producto.getStockComprometido()) - detalle.getCantidad()));
        }

        pedido.setEstado(EstadoPedido.X);
        envioService.pedidoCancelado(pedido);
        Pedido guardado = pedidoRepository.save(pedido);
        notificationPublisher.pedidoCancelado(guardado);
        return pedidoMapper.toResponse(guardado);
    }

    @Transactional(readOnly = true)
    public PageResponse<PedidoResumenResponse> listar(String emailSolicitante, boolean esAdmin, Pageable pageable) {
        // El admin ve todas las ventas; las compras a proveedores tienen su propio módulo.
        var page = esAdmin
                ? pedidoRepository.findByTipo(TipoPedido.Venta, pageable)
                : pedidoRepository.findByUsuarioEmail(emailSolicitante, pageable);
        return PageResponse.from(page.map(pedidoMapper::toResumen));
    }

    @Transactional(readOnly = true)
    public PedidoResponse obtener(Long id, String emailSolicitante, boolean esAdmin) {
        Pedido pedido = buscarConDetalles(id);
        if (!esAdmin && !pedido.getUsuario().getEmail().equals(emailSolicitante)) {
            throw new AccessDeniedException("No puede ver pedidos de otro usuario");
        }
        return pedidoMapper.toResponse(pedido);
    }

    private Pedido buscarConDetalles(Long id) {
        return pedidoRepository.findWithDetallesById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ventas.pedido.no.encontrado", id));
    }

    /**
     * Cada acción trae la clave de su propio mensaje en vez de pasar el verbo
     * como argumento: una frase armada pegando un verbo suelto se traduce mal,
     * porque en otros idiomas el verbo no va necesariamente en ese lugar.
     */
    private void exigirEstado(Pedido pedido, EstadoPedido esperado, String claveMensaje) {
        if (pedido.getEstado() != esperado) {
            throw new BusinessException(claveMensaje, esperado, pedido.getEstado());
        }
    }

    private void bloquearProductosDe(Pedido pedido) {
        productoRepository.findAllForUpdate(pedido.getDetalles().stream()
                .map(detalle -> detalle.getProducto().getId())
                .collect(java.util.stream.Collectors.toSet()));
    }

    private static int nvl(Integer valor) {
        return valor == null ? 0 : valor;
    }
}
