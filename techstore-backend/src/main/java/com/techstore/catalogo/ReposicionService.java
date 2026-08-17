package com.techstore.catalogo;

import com.techstore.ventas.EstadoPedido;
import com.techstore.ventas.PedidoDetalleRepository;
import com.techstore.ventas.TipoPedido;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Calcula a partir de qué stock conviene reponer cada producto.
 *
 * <p>Reemplaza al número fijo cargado a mano por lo que dice el historial:
 * cuánto se vende por día y cuánto tarda el proveedor en traerlo. El mínimo
 * manual sigue valiendo como piso, para los productos que todavía no tienen
 * historial y para cuando alguien quiere forzar un valor.
 */
@Service
@RequiredArgsConstructor
public class ReposicionService {

    /**
     * Estados que cuentan como demanda: una devolución también fue una venta, y
     * hubo que reponer la unidad igual. Las anuladas no, porque esas nunca
     * salieron del depósito.
     */
    private static final List<EstadoPedido> VENDIDOS = List.of(EstadoPedido.G, EstadoPedido.R);

    private final PedidoDetalleRepository pedidoDetalleRepository;

    /** Cuántos días de venta se quieren de colchón por si el proveedor se atrasa. */
    @Value("${reposicion.dias-seguridad:7}")
    private int diasSeguridad;

    /** Ventana de ventas que se mira para estimar la demanda diaria. */
    @Value("${reposicion.dias-historial-ventas:90}")
    private int diasHistorial;

    /**
     * Cuánto se tarda en reponer cuando el producto todavía no tiene ninguna
     * compra recibida. Es un supuesto, y conservador a propósito: subestimarlo
     * hace que el aviso llegue tarde, que es el error caro.
     */
    @Value("${reposicion.dias-entrega-por-defecto:15}")
    private double diasEntregaPorDefecto;

    /** El punto de reposición de cada producto, por id. */
    @Transactional(readOnly = true)
    public Map<Long, PuntoDeReposicion> calcularTodos(Iterable<Producto> productos) {
        List<Producto> lista = new ArrayList<>();
        productos.forEach(lista::add);
        Map<Long, Double> demandaDiaria = demandaDiariaPorProducto(lista);
        Map<Long, Double> diasEntrega = diasDeEntregaPorProducto();

        Map<Long, PuntoDeReposicion> resultado = new HashMap<>();
        for (Producto producto : lista) {
            resultado.put(producto.getId(), calcular(producto, demandaDiaria, diasEntrega));
        }
        return resultado;
    }

    /** El punto de reposición de un producto suelto. */
    @Transactional(readOnly = true)
    public PuntoDeReposicion calcular(Producto producto) {
        return calcular(producto, demandaDiariaPorProducto(List.of(producto)), diasDeEntregaPorProducto());
    }

    private PuntoDeReposicion calcular(Producto producto,
                                       Map<Long, Double> demandaPorProducto,
                                       Map<Long, Double> entregaPorProducto) {
        Double demanda = demandaPorProducto.get(producto.getId());
        if (demanda == null || demanda <= 0) {
            // Sin ventas en la ventana no hay nada que estimar: se respeta el
            // mínimo que cargó una persona, que es la única información que hay.
            return PuntoDeReposicion.manual(producto.umbralEfectivo());
        }

        double entrega = entregaPorProducto.getOrDefault(producto.getId(), diasEntregaPorDefecto);
        // Lo que se vende mientras llega el pedido, más el colchón de seguridad.
        double unidades = demanda * (entrega + diasSeguridad);

        // Se redondea para arriba: pedir de más cuesta el dinero inmovilizado;
        // pedir de menos cuesta la venta que no se pudo hacer.
        int punto = (int) Math.ceil(unidades);
        // Nunca por debajo del mínimo manual: si alguien lo puso, sabe algo que
        // el historial no dice todavía.
        return new PuntoDeReposicion(Math.max(punto, producto.getStockMinimo() == null ? 0 : producto.getStockMinimo()),
                demanda, entrega, true);
    }

    private Map<Long, Double> demandaDiariaPorProducto(Iterable<Producto> productos) {
        LocalDateTime ahora = LocalDateTime.now();
        LocalDateTime desde = ahora.minusDays(diasHistorial);
        Map<Long, Producto> porId = new HashMap<>();
        productos.forEach(producto -> porId.put(producto.getId(), producto));
        Map<Long, Double> demanda = new HashMap<>();
        for (Object[] fila : pedidoDetalleRepository.unidadesVendidasDesde(
                TipoPedido.Venta, VENDIDOS, desde)) {
            Long productoId = (Long) fila[0];
            long unidades = ((Number) fila[1]).longValue();
            Producto producto = porId.get(productoId);
            int divisor = producto == null ? diasHistorial : diasObservados(producto, ahora.toLocalDate());
            demanda.put(productoId, (double) unidades / divisor);
        }
        return demanda;
    }

    /**
     * Un producto nuevo no pudo venderse antes de existir. Dividir sus ventas
     * por toda la ventana de 90 días escondería su velocidad real.
     */
    int diasObservados(Producto producto, LocalDate hoy) {
        if (producto.getCreatedAt() == null) {
            return diasHistorial;
        }
        long desdeAlta = ChronoUnit.DAYS.between(producto.getCreatedAt().toLocalDate(), hoy) + 1;
        return (int) Math.max(1, Math.min(diasHistorial, desdeAlta));
    }

    private Map<Long, Double> diasDeEntregaPorProducto() {
        Map<Long, Double> entrega = new HashMap<>();
        for (Object[] fila : pedidoDetalleRepository.diasDeEntregaPorProducto()) {
            double dias = ((Number) fila[1]).doubleValue();
            // Una compra recibida el mismo día daría 0 y volvería el punto de
            // reposición igual al colchón: se toma medio día como piso.
            entrega.put(((Number) fila[0]).longValue(), Math.max(dias, 0.5));
        }
        return entrega;
    }
}
