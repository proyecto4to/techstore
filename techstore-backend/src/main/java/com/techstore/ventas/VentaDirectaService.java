package com.techstore.ventas;

import com.techstore.catalogo.Producto;
import com.techstore.catalogo.ProductoRepository;
import com.techstore.comun.AutonumeradorService;
import com.techstore.comun.BusinessException;
import com.techstore.comun.Dinero;
import com.techstore.comun.Moneda;
import com.techstore.comun.ResourceNotFoundException;
import com.techstore.facturacion.PagoResponse;
import com.techstore.facturacion.PagoService;
import com.techstore.facturacion.RegistroPagoRequest;
import com.techstore.usuarios.Usuario;
import com.techstore.usuarios.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Venta en mostrador: el cliente compra en el local sin pedido previo por la web.
 *
 * <p>A diferencia del checkout web, acá no hay nada que reservar: la mercadería
 * sale en el momento, así que el pedido nace Confirmado con el stock ya
 * descontado. El cobro y la factura los hace {@link PagoService}, el mismo
 * camino que una venta web, para no tener dos formas de emitir una factura.
 *
 * <p>El pedido queda a nombre del cajero que lo hizo (es quien responde por la
 * venta); a quién se le factura lo define el receptor de la factura, que puede
 * ser un RUC o consumidor final.
 */
@Service
@RequiredArgsConstructor
public class VentaDirectaService {

    private static final String ENTIDAD_PEDIDO = "PEDIDO";

    private final PedidoRepository pedidoRepository;
    private final ProductoRepository productoRepository;
    private final UsuarioRepository usuarioRepository;
    private final AutonumeradorService autonumeradorService;
    private final PagoService pagoService;

    @Transactional
    public PagoResponse vender(VentaDirectaRequest request, String emailCajero) {
        Usuario cajero = usuarioRepository.findByEmail(emailCajero)
                .orElseThrow(() -> new BadCredentialsException("Usuario no encontrado"));

        // Fusiona items repetidos del mismo producto (la BD exige unicidad pedido+producto).
        Map<Long, Integer> cantidades = new LinkedHashMap<>();
        for (VentaDirectaRequest.ItemVenta item : request.items()) {
            cantidades.merge(item.productoId(), item.cantidad(), Integer::sum);
        }

        // Bloqueo pesimista: dos ventas simultáneas no pueden vender el mismo stock.
        Map<Long, Producto> productos = productoRepository.findAllForUpdate(cantidades.keySet()).stream()
                .collect(LinkedHashMap::new, (map, p) -> map.put(p.getId(), p), Map::putAll);

        Pedido pedido = new Pedido();
        pedido.setTipo(TipoPedido.Venta);
        pedido.setEstado(EstadoPedido.C);
        pedido.setUsuario(cajero);
        pedido.setNumero(autonumeradorService.siguiente(ENTIDAD_PEDIDO));
        pedido.setNotas(notasDeVenta(request.notas()));
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
            // Se compara contra el disponible: lo comprometido por pedidos web no se puede vender acá.
            if (cantidad > producto.getStockDisponible()) {
                throw new BusinessException("Stock insuficiente para '%s': disponibles %d, solicitados %d"
                        .formatted(producto.getNombre(), producto.getStockDisponible(), cantidad));
            }

            // La mercadería sale ahora: se descuenta del stock real, sin pasar por comprometido.
            producto.setStockReal(nvl(producto.getStockReal()) - cantidad);

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
        Pedido guardado = pedidoRepository.saveAndFlush(pedido);

        // Mismo cobro que una venta web: pagos, factura con su receptor, caja y correo.
        return pagoService.registrarPago(
                guardado.getId(),
                new RegistroPagoRequest(request.lineas(), request.receptor()),
                emailCajero);
    }

    /** Deja marcado que fue en mostrador, para distinguirla de las ventas web. */
    private static String notasDeVenta(String notas) {
        String marca = "Venta directa en mostrador";
        return notas == null || notas.isBlank() ? marca : marca + " — " + notas.trim();
    }

    private static int nvl(Integer valor) {
        return valor == null ? 0 : valor;
    }
}
