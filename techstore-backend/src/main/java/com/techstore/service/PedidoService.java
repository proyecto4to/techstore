package com.techstore.service;

import com.techstore.dto.request.CheckoutRequest;
import com.techstore.dto.response.PageResponse;
import com.techstore.dto.response.PedidoResponse;
import com.techstore.dto.response.PedidoResumenResponse;
import com.techstore.entity.EstadoPedido;
import com.techstore.entity.Pedido;
import com.techstore.entity.PedidoDetalle;
import com.techstore.entity.Producto;
import com.techstore.entity.TipoCorreo;
import com.techstore.entity.TipoPedido;
import com.techstore.entity.Usuario;
import com.techstore.exception.BusinessException;
import com.techstore.exception.ResourceNotFoundException;
import com.techstore.mapper.PedidoMapper;
import com.techstore.repository.PedidoRepository;
import com.techstore.repository.ProductoRepository;
import com.techstore.repository.UsuarioRepository;
import com.techstore.util.Dinero;
import com.techstore.util.Moneda;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PedidoService {

    private static final String ENTIDAD_PEDIDO = "PEDIDO";

    private final PedidoRepository pedidoRepository;
    private final ProductoRepository productoRepository;
    private final UsuarioRepository usuarioRepository;
    private final AutonumeradorService autonumeradorService;
    private final PedidoMapper pedidoMapper;
    private final CorreoService correoService;
    private final EmailTemplateService emailTemplateService;

    /**
     * Crea un pedido de venta en estado Pendiente comprometiendo el stock.
     * Las filas de productos se bloquean durante la transacción para que dos
     * checkouts simultáneos no puedan sobrevender el mismo stock.
     */
    @Transactional
    public PedidoResponse checkout(String emailUsuario, CheckoutRequest request) {
        Usuario usuario = usuarioRepository.findByEmail(emailUsuario)
                .orElseThrow(() -> new BadCredentialsException("Usuario no encontrado"));

        // Fusiona items repetidos del mismo producto (la BD exige unicidad pedido+producto).
        Map<Long, Integer> cantidades = new LinkedHashMap<>();
        for (CheckoutRequest.ItemCarrito item : request.items()) {
            cantidades.merge(item.productoId(), item.cantidad(), Integer::sum);
        }

        Map<Long, Producto> productos = productoRepository.findAllForUpdate(cantidades.keySet()).stream()
                .collect(LinkedHashMap::new, (map, p) -> map.put(p.getId(), p), Map::putAll);

        Pedido pedido = new Pedido();
        pedido.setTipo(TipoPedido.Venta);
        pedido.setEstado(EstadoPedido.P);
        pedido.setUsuario(usuario);
        pedido.setNotas(request.notas());
        pedido.setNumero(autonumeradorService.siguiente(ENTIDAD_PEDIDO));
        // La moneda del pedido es la del catálogo; los detalles heredan la suya de acá.
        pedido.setMoneda(Moneda.porDefecto());

        Dinero total = Dinero.cero(pedido.getMoneda());
        for (Map.Entry<Long, Integer> entrada : cantidades.entrySet()) {
            Producto producto = productos.get(entrada.getKey());
            if (producto == null) {
                throw new ResourceNotFoundException("Producto", entrada.getKey());
            }
            if (!Boolean.TRUE.equals(producto.getActivo())) {
                throw new BusinessException("El producto '%s' ya no está disponible".formatted(producto.getNombre()));
            }
            int cantidad = entrada.getValue();
            if (cantidad > producto.getStockDisponible()) {
                throw new BusinessException("Stock insuficiente para '%s': disponibles %d, solicitados %d"
                        .formatted(producto.getNombre(), producto.getStockDisponible(), cantidad));
            }

            producto.setStockComprometido(nvl(producto.getStockComprometido()) + cantidad);

            PedidoDetalle detalle = new PedidoDetalle();
            detalle.setProducto(producto);
            detalle.setCantidad(cantidad);
            detalle.setIvaAplicado(producto.getIva());
            // Se agrega antes de cargarle importes: la moneda del detalle es la del pedido.
            pedido.agregarDetalle(detalle);
            detalle.setPrecioUnitario(producto.getPrecio());
            detalle.setSubtotal(producto.getPrecio().por(cantidad));

            total = total.mas(detalle.getSubtotal());
        }

        pedido.setTotal(total);
        Pedido guardado = pedidoRepository.save(pedido);
        enviarConfirmacionPedido(usuario, guardado);
        return pedidoMapper.toResponse(guardado);
    }

    /** Avisa al cliente por correo que recibimos su pedido (bandeja de salida). */
    private void enviarConfirmacionPedido(Usuario cliente, Pedido pedido) {
        String cuerpo = emailTemplateService.construir(
                TipoCorreo.PEDIDO,
                "¡Recibimos tu pedido!",
                cliente.getNombre(),
                "Tu pedido N.º " + pedido.getNumero() + " quedó en estado Pendiente y el stock reservado "
                        + "a tu nombre. Cuando lo confirmemos y cobremos, te enviaremos la factura por este medio.",
                "Total: " + pedido.getTotal(),
                "VER MIS PEDIDOS",
                emailTemplateService.url("/pedidos"));
        correoService.enviar(
                TipoCorreo.PEDIDO,
                cliente.getEmail(),
                cliente.getNombre() + " " + cliente.getApellido(),
                "Recibimos tu pedido N.º " + pedido.getNumero() + " — TechStore",
                cuerpo,
                "PEDIDO-" + pedido.getNumero());
    }

    /**
     * Confirma un pedido pendiente: descuenta el stock real y libera el comprometido.
     */
    @Transactional
    public PedidoResponse confirmar(Long id) {
        Pedido pedido = buscarConDetalles(id);
        exigirEstado(pedido, EstadoPedido.P, "confirmar");

        bloquearProductosDe(pedido);
        for (PedidoDetalle detalle : pedido.getDetalles()) {
            Producto producto = detalle.getProducto();
            int nuevoStock = nvl(producto.getStockReal()) - detalle.getCantidad();
            if (nuevoStock < 0) {
                throw new BusinessException("El stock real de '%s' quedaría negativo".formatted(producto.getNombre()));
            }
            producto.setStockReal(nuevoStock);
            producto.setStockComprometido(Math.max(0, nvl(producto.getStockComprometido()) - detalle.getCantidad()));
        }

        pedido.setEstado(EstadoPedido.C);
        return pedidoMapper.toResponse(pedidoRepository.save(pedido));
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
        exigirEstado(pedido, EstadoPedido.P, "anular");

        bloquearProductosDe(pedido);
        for (PedidoDetalle detalle : pedido.getDetalles()) {
            Producto producto = detalle.getProducto();
            producto.setStockComprometido(Math.max(0, nvl(producto.getStockComprometido()) - detalle.getCantidad()));
        }

        pedido.setEstado(EstadoPedido.X);
        return pedidoMapper.toResponse(pedidoRepository.save(pedido));
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
                .orElseThrow(() -> new ResourceNotFoundException("Pedido", id));
    }

    private void exigirEstado(Pedido pedido, EstadoPedido esperado, String accion) {
        if (pedido.getEstado() != esperado) {
            throw new BusinessException("Solo se puede %s un pedido en estado %s (actual: %s)"
                    .formatted(accion, esperado, pedido.getEstado()));
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
