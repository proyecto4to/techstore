package com.techstore.compras;

import com.techstore.catalogo.Producto;
import com.techstore.catalogo.ProductoRepository;
import com.techstore.comun.Dinero;
import com.techstore.comun.Moneda;
import com.techstore.ventas.PedidoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Comparar proveedores antes de comprar")
class ComparadorProveedoresServiceTest {

    @Mock
    private ProveedorProductoRepository ofertaRepository;

    @Mock
    private ProductoRepository productoRepository;

    @Mock
    private PedidoRepository pedidoRepository;

    private ComparadorProveedoresService servicio;

    private Producto teclado;
    private Producto mouse;
    private Proveedor barato;
    private Proveedor completo;

    @BeforeEach
    void setUp() {
        servicio = new ComparadorProveedoresService(ofertaRepository, productoRepository, pedidoRepository);

        teclado = producto(1L, "Teclado mecánico");
        mouse = producto(2L, "Mouse inalámbrico");
        barato = proveedor(10L, "Importadora Barata SA");
        completo = proveedor(20L, "Distribuidora Completa SRL");
    }

    private static Producto producto(Long id, String nombre) {
        Producto p = new Producto();
        p.setId(id);
        p.setNombre(nombre);
        return p;
    }

    private static Proveedor proveedor(Long id, String razonSocial) {
        Proveedor p = new Proveedor();
        p.setId(id);
        p.setRazonSocial(razonSocial);
        p.setActivo(true);
        return p;
    }

    private static ProveedorProducto oferta(Proveedor proveedor, Producto producto, long precio) {
        return oferta(proveedor, producto, precio, Moneda.PYG);
    }

    private static ProveedorProducto oferta(
            Proveedor proveedor, Producto producto, long precio, Moneda moneda) {
        ProveedorProducto o = new ProveedorProducto();
        o.setProveedor(proveedor);
        o.setProducto(producto);
        o.setMoneda(moneda);
        o.setPrecio(Dinero.de(precio, moneda));
        o.setDisponible(true);
        return o;
    }

    private CompararProveedoresRequest pedir(Object... productoYCantidad) {
        List<CompararProveedoresRequest.Item> items = new java.util.ArrayList<>();
        for (int i = 0; i < productoYCantidad.length; i += 2) {
            items.add(new CompararProveedoresRequest.Item(
                    (Long) productoYCantidad[i], (Integer) productoYCantidad[i + 1]));
        }
        return new CompararProveedoresRequest(items);
    }

    @Test
    @DisplayName("el que cubre todo va primero, aunque salga más caro")
    void laCoberturaPesaMasQueElPrecio() {
        when(productoRepository.findAllById(anyCollection())).thenReturn(List.of(teclado, mouse));
        when(ofertaRepository.findByProductoIdIn(anyCollection())).thenReturn(List.of(
                // El barato solo tiene el teclado, y más barato.
                oferta(barato, teclado, 80_000),
                // El completo tiene los dos, el teclado más caro.
                oferta(completo, teclado, 100_000),
                oferta(completo, mouse, 50_000)));
        lenient().when(pedidoRepository.historialPorProveedor()).thenReturn(List.of());

        List<ComparacionProveedorResponse> resultado = servicio.comparar(pedir(1L, 1, 2L, 1));

        assertEquals(2, resultado.size());
        // Una compra a medias obliga a un segundo pedido: eso cuesta más que la
        // diferencia de precio, así que el completo va arriba.
        assertEquals("Distribuidora Completa SRL", resultado.get(0).razonSocial());
        assertTrue(resultado.get(0).coberturaCompleta());
        assertEquals(new BigDecimal("150000"), resultado.get(0).total());

        assertFalse(resultado.get(1).coberturaCompleta());
        assertEquals(List.of("Mouse inalámbrico"), resultado.get(1).faltantes());
    }

    @Test
    @DisplayName("entre los que cubren todo, primero el más barato")
    void aIgualCoberturaGanaElPrecio() {
        when(productoRepository.findAllById(anyCollection())).thenReturn(List.of(teclado));
        when(ofertaRepository.findByProductoIdIn(anyCollection())).thenReturn(List.of(
                oferta(completo, teclado, 100_000),
                oferta(barato, teclado, 80_000)));
        lenient().when(pedidoRepository.historialPorProveedor()).thenReturn(List.of());

        List<ComparacionProveedorResponse> resultado = servicio.comparar(pedir(1L, 2));

        assertEquals("Importadora Barata SA", resultado.get(0).razonSocial());
        assertEquals(new BigDecimal("160000"), resultado.get(0).total(), "80.000 × 2 unidades");
    }

    @Test
    @DisplayName("la fiabilidad sale del historial: cuántas compras llegaron")
    void laFiabilidadEsElPorcentajeQueLlego() {
        when(productoRepository.findAllById(anyCollection())).thenReturn(List.of(teclado));
        when(ofertaRepository.findByProductoIdIn(anyCollection())).thenReturn(List.of(
                oferta(completo, teclado, 100_000)));
        // 3 recibidas y 1 anulada = 75%, con 12 días promedio de entrega.
        when(pedidoRepository.historialPorProveedor()).thenReturn(List.<Object[]>of(
                new Object[]{20L, 12.0, 3L, 1L}));

        ComparacionProveedorResponse fila = servicio.comparar(pedir(1L, 1)).get(0);

        assertEquals(75.0, fila.fiabilidad());
        assertEquals(12.0, fila.diasEntregaPromedio());
        assertEquals(3L, fila.comprasRecibidas(), "Sobre cuántas entregas se apoya el número");
    }

    @Test
    @DisplayName("sin historial no se inventa una fiabilidad")
    void elProveedorNuevoNoTieneNota() {
        when(productoRepository.findAllById(anyCollection())).thenReturn(List.of(teclado));
        when(ofertaRepository.findByProductoIdIn(anyCollection())).thenReturn(List.of(
                oferta(barato, teclado, 80_000)));
        when(pedidoRepository.historialPorProveedor()).thenReturn(List.of());

        ComparacionProveedorResponse fila = servicio.comparar(pedir(1L, 1)).get(0);

        // Nulo y no 0 ni 100: no sabemos nada de él todavía, y cualquiera de los
        // dos números sería una afirmación falsa.
        assertNull(fila.fiabilidad());
        assertNull(fila.diasEntregaPromedio());
        assertEquals(0L, fila.comprasRecibidas());
    }

    @Test
    @DisplayName("una oferta apagada no cotiza")
    void loQueElProveedorNoTieneNoSeOfrece() {
        ProveedorProducto sinStock = oferta(barato, teclado, 80_000);
        sinStock.setDisponible(false);

        when(productoRepository.findAllById(anyCollection())).thenReturn(List.of(teclado));
        when(ofertaRepository.findByProductoIdIn(anyCollection())).thenReturn(List.of(
                sinStock, oferta(completo, teclado, 100_000)));
        lenient().when(pedidoRepository.historialPorProveedor()).thenReturn(List.of());

        List<ComparacionProveedorResponse> resultado = servicio.comparar(pedir(1L, 1));

        // El barato queda fuera aunque su precio siga guardado como referencia.
        assertEquals(1, resultado.size());
        assertEquals("Distribuidora Completa SRL", resultado.get(0).razonSocial());
    }

    @Test
    @DisplayName("el mismo producto pedido dos veces se suma en una sola línea")
    void lasCantidadesRepetidasSeFusionan() {
        when(productoRepository.findAllById(anyCollection())).thenReturn(List.of(teclado));
        when(ofertaRepository.findByProductoIdIn(anyCollection())).thenReturn(List.of(
                oferta(barato, teclado, 80_000)));
        lenient().when(pedidoRepository.historialPorProveedor()).thenReturn(List.of());

        ComparacionProveedorResponse fila = servicio.comparar(pedir(1L, 2, 1L, 3)).get(0);

        assertEquals(1, fila.cubre().size());
        assertEquals(5, fila.cubre().get(0).cantidad());
        assertEquals(new BigDecimal("400000"), fila.total());
    }

    @Test
    @DisplayName("las monedas distintas se totalizan por separado y nunca se suman entre sí")
    void lasMonedasDistintasNoSeMezclan() {
        when(productoRepository.findAllById(anyCollection())).thenReturn(List.of(teclado, mouse));
        when(ofertaRepository.findByProductoIdIn(anyCollection())).thenReturn(List.of(
                oferta(completo, teclado, 100_000, Moneda.PYG),
                oferta(completo, mouse, 50, Moneda.USD)));
        lenient().when(pedidoRepository.historialPorProveedor()).thenReturn(List.of());

        List<ComparacionProveedorResponse> resultado = servicio.comparar(pedir(1L, 1, 2L, 2));

        assertEquals(2, resultado.size());
        assertEquals(List.of("PYG", "USD"),
                resultado.stream().map(ComparacionProveedorResponse::moneda).sorted().toList());
        assertTrue(resultado.stream().noneMatch(ComparacionProveedorResponse::coberturaCompleta));
        assertTrue(resultado.stream().anyMatch(f -> f.moneda().equals("PYG")
                && f.total().compareTo(new BigDecimal("100000")) == 0));
        assertTrue(resultado.stream().anyMatch(f -> f.moneda().equals("USD")
                && f.total().compareTo(new BigDecimal("100")) == 0));
    }
}
