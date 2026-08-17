package com.techstore.compras;

import com.techstore.catalogo.Producto;
import com.techstore.catalogo.ProductoRepository;
import com.techstore.comun.Dinero;
import com.techstore.comun.Moneda;
import com.techstore.comun.ResourceNotFoundException;
import com.techstore.ventas.PedidoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Arma la comparación de proveedores para una compra: quién tiene qué, a qué
 * precio, cuánto tarda y qué tan confiable viene siendo.
 *
 * <p>El sistema no elige: ordena la información y la decisión queda en el
 * usuario, que puede estar ponderando cosas que acá no están (una deuda con un
 * proveedor, un favor pendiente, la calidad de una marca).
 */
@Service
@RequiredArgsConstructor
public class ComparadorProveedoresService {

    private final ProveedorProductoRepository ofertaRepository;
    private final ProductoRepository productoRepository;
    private final PedidoRepository pedidoRepository;

    @Transactional(readOnly = true)
    public List<ComparacionProveedorResponse> comparar(CompararProveedoresRequest request) {
        // Cantidades fusionadas por producto, como en toda carga de items.
        Map<Long, Integer> cantidades = new LinkedHashMap<>();
        for (CompararProveedoresRequest.Item item : request.items()) {
            cantidades.merge(item.productoId(), item.cantidad(), Integer::sum);
        }

        Map<Long, Producto> productos = new HashMap<>();
        for (Producto p : productoRepository.findAllById(cantidades.keySet())) {
            productos.put(p.getId(), p);
        }
        for (Long id : cantidades.keySet()) {
            if (!productos.containsKey(id)) {
                throw new ResourceNotFoundException("catalogo.producto.no.encontrado", id);
            }
        }

        // Ofertas vigentes agrupadas por proveedor. Las apagadas (disponible =
        // false) no cotizan: son solo referencia de precio.
        Map<ProveedorMoneda, List<ProveedorProducto>> porProveedor = new LinkedHashMap<>();
        for (ProveedorProducto oferta : ofertaRepository.findByProductoIdIn(cantidades.keySet())) {
            if (Boolean.TRUE.equals(oferta.getDisponible())
                    && Boolean.TRUE.equals(oferta.getProveedor().getActivo())) {
                ProveedorMoneda clave = new ProveedorMoneda(
                        oferta.getProveedor().getId(), oferta.getMoneda());
                porProveedor.computeIfAbsent(clave, k -> new ArrayList<>()).add(oferta);
            }
        }

        Map<Long, Historial> historial = historialPorProveedor();

        List<ComparacionProveedorResponse> comparacion = new ArrayList<>();
        for (List<ProveedorProducto> ofertas : porProveedor.values()) {
            comparacion.add(armarFila(ofertas, cantidades, productos, historial));
        }

        // Primero los que cubren todo; dentro de cada moneda, el más barato. La
        // rapidez y la fiabilidad se muestran pero no ordenan: cuánto pesan es
        // una decisión de quien compra, no del sistema.
        comparacion.sort(Comparator
                .comparing((ComparacionProveedorResponse c) -> !c.coberturaCompleta())
                // Importes de monedas distintas no son comparables sin una
                // cotización. Se agrupan por código y solo entonces por total.
                .thenComparing(ComparacionProveedorResponse::moneda)
                .thenComparing(ComparacionProveedorResponse::total));
        return comparacion;
    }

    private ComparacionProveedorResponse armarFila(List<ProveedorProducto> ofertas,
                                                   Map<Long, Integer> cantidades,
                                                   Map<Long, Producto> productos,
                                                   Map<Long, Historial> historiales) {
        Proveedor proveedor = ofertas.get(0).getProveedor();
        Moneda moneda = ofertas.get(0).getMoneda();

        List<ComparacionProveedorResponse.LineaCotizada> cubre = new ArrayList<>();
        Dinero total = Dinero.cero(moneda);
        for (ProveedorProducto oferta : ofertas) {
            int cantidad = cantidades.get(oferta.getProducto().getId());
            Dinero subtotal = oferta.getPrecio().por(cantidad);
            cubre.add(new ComparacionProveedorResponse.LineaCotizada(
                    oferta.getProducto().getId(),
                    oferta.getProducto().getNombre(),
                    cantidad,
                    oferta.getPrecio().monto(),
                    subtotal.monto()));
            total = total.mas(subtotal);
        }

        List<String> faltantes = cantidades.keySet().stream()
                .filter(id -> ofertas.stream().noneMatch(o -> o.getProducto().getId().equals(id)))
                .map(id -> productos.get(id).getNombre())
                .toList();

        Historial h = historiales.getOrDefault(proveedor.getId(), Historial.VACIO);
        return new ComparacionProveedorResponse(
                proveedor.getId(),
                proveedor.getRazonSocial(),
                faltantes.isEmpty(),
                cubre,
                faltantes,
                moneda.codigo(),
                total.monto(),
                h.diasEntrega(),
                h.fiabilidad(),
                h.recibidas());
    }

    private Map<Long, Historial> historialPorProveedor() {
        Map<Long, Historial> resultado = new HashMap<>();
        for (Object[] fila : pedidoRepository.historialPorProveedor()) {
            long recibidas = ((Number) fila[2]).longValue();
            long anuladas = ((Number) fila[3]).longValue();
            resultado.put(((Number) fila[0]).longValue(), new Historial(
                    fila[1] == null ? null : ((Number) fila[1]).doubleValue(),
                    recibidas, anuladas));
        }
        return resultado;
    }

    /**
     * Lo que el historial dice de un proveedor. La fiabilidad es el porcentaje
     * de compras cerradas que llegaron: no mide la calidad de la mercadería,
     * mide si lo que se le pidió, llegó.
     */
    private record Historial(Double diasEntrega, long recibidas, long anuladas) {

        static final Historial VACIO = new Historial(null, 0, 0);

        Double fiabilidad() {
            long cerradas = recibidas + anuladas;
            if (cerradas == 0) {
                return null;
            }
            return recibidas * 100.0 / cerradas;
        }
    }

    /** Una cotización solo puede totalizar importes de la misma moneda. */
    private record ProveedorMoneda(Long proveedorId, Moneda moneda) {
    }
}
