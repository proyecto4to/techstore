package com.techstore.ventas;

import com.techstore.catalogo.Producto;
import com.techstore.catalogo.ProductoMapper;
import com.techstore.catalogo.ProductoRepository;
import com.techstore.comun.Dinero;
import com.techstore.promociones.CalculadorDePrecios;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CarritoService {

    private static final int CANTIDAD_MAXIMA = Producto.MAXIMO_POR_PEDIDO;

    private final ProductoRepository productoRepository;
    private final ProductoMapper productoMapper;
    private final CalculadorDePrecios calculadorDePrecios;

    /**
     * Recalcula el carrito con el catálogo del servidor. No reserva stock: la
     * reserva transaccional sigue ocurriendo únicamente durante el checkout.
     */
    @Transactional(readOnly = true)
    public ValidacionCarritoResponse validar(ValidarCarritoRequest request) {
        var vigentes = calculadorDePrecios.vigentes();
        Map<Long, Integer> cantidades = new LinkedHashMap<>();
        for (ValidarCarritoRequest.Item item : request.items()) {
            cantidades.merge(item.productoId(), item.cantidad(), Math::addExact);
        }

        Map<Long, Producto> productos = productoRepository.findAllById(cantidades.keySet()).stream()
                .collect(LinkedHashMap::new, (map, producto) -> map.put(producto.getId(), producto), Map::putAll);

        List<ValidacionCarritoResponse.Linea> lineas = cantidades.entrySet().stream()
                .map(entrada -> validarLinea(
                        entrada.getKey(), entrada.getValue(), productos.get(entrada.getKey()), vigentes))
                .toList();
        boolean valido = lineas.stream().allMatch(linea -> linea.estado() == EstadoLineaCarrito.DISPONIBLE);

        Map<String, BigDecimal> totales = new LinkedHashMap<>();
        if (valido) {
            for (ValidacionCarritoResponse.Linea linea : lineas) {
                totales.merge(linea.producto().moneda(), linea.subtotal(), BigDecimal::add);
            }
        }
        return new ValidacionCarritoResponse(valido, lineas, totales);
    }

    private ValidacionCarritoResponse.Linea validarLinea(
            Long productoId,
            int cantidad,
            Producto producto,
            CalculadorDePrecios.PromocionesVigentes vigentes) {
        if (cantidad > CANTIDAD_MAXIMA) {
            return new ValidacionCarritoResponse.Linea(
                    productoId, cantidad, EstadoLineaCarrito.CANTIDAD_EXCESIVA, null, null);
        }
        if (producto == null) {
            return new ValidacionCarritoResponse.Linea(
                    productoId, cantidad, EstadoLineaCarrito.NO_ENCONTRADO, null, null);
        }
        if (!Boolean.TRUE.equals(producto.getActivo())) {
            return new ValidacionCarritoResponse.Linea(
                    productoId, cantidad, EstadoLineaCarrito.NO_DISPONIBLE, null, null);
        }

        // El precio del carrito sale del mismo cálculo que el del catálogo y
        // que el del checkout: son tres pantallas de un solo número.
        var precio = vigentes.precioDe(producto);
        var publico = productoMapper.toPublicResponse(producto).con(precio);
        if (producto.getStockDisponible() <= 0) {
            return new ValidacionCarritoResponse.Linea(
                    productoId, cantidad, EstadoLineaCarrito.NO_DISPONIBLE, publico, null);
        }
        if (cantidad > producto.getStockDisponible()) {
            return new ValidacionCarritoResponse.Linea(
                    productoId, cantidad, EstadoLineaCarrito.CANTIDAD_INSUFICIENTE, publico, null);
        }

        Dinero subtotal = precio.precio().por(cantidad);
        return new ValidacionCarritoResponse.Linea(
                productoId, cantidad, EstadoLineaCarrito.DISPONIBLE, publico, subtotal.monto());
    }
}
