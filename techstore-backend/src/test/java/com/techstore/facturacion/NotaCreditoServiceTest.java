package com.techstore.facturacion;

import com.techstore.auditoria.AuditoriaService;
import com.techstore.caja.CajaService;
import com.techstore.catalogo.Producto;
import com.techstore.catalogo.ProductoRepository;
import com.techstore.comun.AutonumeradorService;
import com.techstore.comun.BusinessException;
import com.techstore.comun.Dinero;
import com.techstore.comun.Moneda;
import com.techstore.empresa.EmpresaProperties;
import com.techstore.facturacion.EmitirNotaCreditoRequest.ItemDevolucion;
import com.techstore.usuarios.Rol;
import com.techstore.usuarios.Usuario;
import com.techstore.usuarios.UsuarioRepository;
import com.techstore.ventas.EstadoPedido;
import com.techstore.ventas.Pedido;
import com.techstore.ventas.PedidoDetalle;
import com.techstore.ventas.PedidoRepository;
import com.techstore.ventas.TipoPedido;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotaCreditoServiceTest {

    @Mock
    private NotaCreditoRepository notaCreditoRepository;

    @Mock
    private NotaCreditoDetalleRepository notaCreditoDetalleRepository;

    @Mock
    private PedidoRepository pedidoRepository;

    @Mock
    private FacturaRepository facturaRepository;

    @Mock
    private ProductoRepository productoRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private AutonumeradorService autonumeradorService;

    @Mock
    private CajaService cajaService;

    private final FacturaPdfService facturaPdfService = new FacturaPdfService(
            FacturaPdfServiceTest.configConEmisor(new EmpresaProperties(
                    "TechStore Paraguay", "80012345-6", "Avda. España 1234 c/ Brasil", "Asunción, Paraguay",
                    "(021) 555-123", "Venta de equipos e insumos informáticos", "12345678",
                    "01/01/2026 al 31/12/2026",
                    2, "001", "001", "47411", "11", "CENTRAL", "1", "ASUNCION", "1", "ASUNCION", "2026-01-01")));

    @Mock
    private AuditoriaService auditoriaService;

    private NotaCreditoService notaCreditoService;
    private Usuario cajero;
    private Pedido pedido;
    private Factura factura;
    private Producto ram;
    private Producto ssd;

    @BeforeEach
    void setUp() {
        notaCreditoService = new NotaCreditoService(
                notaCreditoRepository, notaCreditoDetalleRepository, pedidoRepository, facturaRepository,
                productoRepository, usuarioRepository, autonumeradorService, cajaService, facturaPdfService,
                FacturaPdfServiceTest.configConEmisor(new EmpresaProperties(
                        "TechStore Paraguay", "80012345-0", null, null, null, null, "12345678", null,
                        2, "001", "001", null, null, null, null, null, null, null, "2026-01-01")),
                auditoriaService);

        Rol rol = new Rol();
        rol.setNombre("Cajero");
        cajero = new Usuario();
        cajero.setId(5L);
        cajero.setEmail("cajero@techstore.py");
        cajero.setNombre("Caja");
        cajero.setApellido("Uno");
        cajero.setRol(rol);

        // Pedido con dos productos: 2 RAM (800.000 c/u) + 1 SSD (600.000) = 2.200.000
        ram = producto(4L, "Kingston 64GB RAM", "800000.00", 9);
        ssd = producto(3L, "Samsung 1TB SSD", "600000.00", 15);

        pedido = new Pedido();
        pedido.setId(3L);
        pedido.setNumero(3);
        pedido.setTipo(TipoPedido.Venta);
        pedido.setEstado(EstadoPedido.G);
        pedido.setUsuario(cajero);
        agregarDetalle(pedido, ram, 2);
        agregarDetalle(pedido, ssd, 1);
        pedido.setTotal(Dinero.de(2_200_000, Moneda.PYG));

        factura = new Factura();
        factura.setId(7L);
        factura.setNumero("001-001-0000002");
        factura.setTipo("CONTADO");
        factura.setPedido(pedido);
        factura.setSubtotal(Dinero.de(2_000_000, Moneda.PYG));
        factura.setIva(Dinero.de(200_000, Moneda.PYG));
        factura.setTotal(Dinero.de(2_200_000, Moneda.PYG));
        factura.setEstado(EstadoFactura.Pagada);
        factura.setUsuario(cajero);
    }

    private Producto producto(Long id, String nombre, String precio, int stock) {
        Producto p = new Producto();
        p.setId(id);
        p.setNombre(nombre);
        p.setPrecio(Dinero.de(new BigDecimal(precio), Moneda.PYG));
        p.setIva(BigDecimal.TEN);
        p.setStockReal(stock);
        p.setStockComprometido(0);
        return p;
    }

    /**
     * El detalle se agrega al pedido antes de cargarle importes: la moneda del
     * detalle es la del pedido, así que sin padre no sabe en qué moneda está.
     */
    private PedidoDetalle agregarDetalle(Pedido pedido, Producto producto, int cantidad) {
        PedidoDetalle d = new PedidoDetalle();
        d.setProducto(producto);
        d.setCantidad(cantidad);
        d.setIvaAplicado(BigDecimal.TEN);
        pedido.agregarDetalle(d);
        d.fijarPrecio(producto.getPrecio(), null);
        return d;
    }

    private void prepararHappy() {
        when(usuarioRepository.findByEmail("cajero@techstore.py")).thenReturn(Optional.of(cajero));
        when(pedidoRepository.findWithDetallesById(3L)).thenReturn(Optional.of(pedido));
        when(facturaRepository.findByPedidoId(3L)).thenReturn(Optional.of(factura));
        when(notaCreditoDetalleRepository.cantidadesDevueltasPorProducto(3L)).thenReturn(List.of());
        lenient().when(productoRepository.findAllForUpdate(anyCollection())).thenReturn(List.of());
        when(autonumeradorService.siguiente("NOTACREDITO")).thenReturn(1);
        when(notaCreditoRepository.save(any(NotaCredito.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(facturaRepository.save(any(Factura.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(pedidoRepository.save(any(Pedido.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private EmitirNotaCreditoRequest req(String motivo, ItemDevolucion... items) {
        return new EmitirNotaCreditoRequest(motivo, List.of(items));
    }

    @Test
    void devolucionParcialSoloDevuelveEseProductoYNoAnulaLaFactura() {
        prepararHappy();

        // Devolver solo 1 de las 2 RAM
        NotaCreditoResponse nc = notaCreditoService.emitir(3L,
                req("Una vino fallada", new ItemDevolucion(4L, 1)), "cajero@techstore.py");

        assertEquals(new BigDecimal("800000"), nc.total());
        assertEquals(1, nc.detalles().size());
        assertEquals(10, ram.getStockReal()); // 9 + 1 devuelta
        assertEquals(15, ssd.getStockReal()); // el SSD no se tocó
        // El pedido NO quedó completamente devuelto: sigue Pagado y la factura vigente
        assertEquals(EstadoPedido.G, pedido.getEstado());
        assertEquals(EstadoFactura.Pagada, factura.getEstado());
        // "NC" adelante: sin el prefijo esta referencia sería idéntica a la de
        // una factura distinta, porque los numeradores son independientes.
        verify(cajaService).registrarEgresoAutomatico(
                eq(Dinero.de(800_000, Moneda.PYG)), eq("Devolución pedido N.º 3"),
                eq("NC 001-001-0000001"), eq(cajero));
    }

    @Test
    void seDevuelveLoCobradoYNoElPrecioDeLista() {
        // La RAM se vendió con 100.000 de rebaja por unidad: 800.000 − 100.000.
        pedido.getDetalles().get(0).fijarPrecio(ram.getPrecio(), Dinero.de(100_000, Moneda.PYG));
        prepararHappy();

        NotaCreditoResponse nc = notaCreditoService.emitir(3L,
                req("Vino fallada", new ItemDevolucion(4L, 1)), "cajero@techstore.py");

        // Si la devolución usara el precio de lista, el cliente cobraría 800.000
        // por algo que pagó 700.000 y se quedaría con la diferencia.
        assertEquals(new BigDecimal("700000"), nc.total());
        verify(cajaService).registrarEgresoAutomatico(
                eq(Dinero.de(700_000, Moneda.PYG)), any(), any(), eq(cajero));
    }

    @Test
    void devolucionTotalAnulaFacturaYMarcaPedidoDevuelto() {
        prepararHappy();

        NotaCreditoResponse nc = notaCreditoService.emitir(3L,
                req("Se arrepintió", new ItemDevolucion(4L, 2), new ItemDevolucion(3L, 1)),
                "cajero@techstore.py");

        assertEquals(new BigDecimal("2200000"), nc.total());
        assertEquals(2, nc.detalles().size());
        assertEquals(11, ram.getStockReal()); // 9 + 2
        assertEquals(16, ssd.getStockReal()); // 15 + 1
        assertEquals(EstadoPedido.R, pedido.getEstado());
        assertEquals(EstadoFactura.Anulada, factura.getEstado());
    }

    @Test
    void noSePuedeDevolverMasDeLoComprado() {
        when(usuarioRepository.findByEmail("cajero@techstore.py")).thenReturn(Optional.of(cajero));
        when(pedidoRepository.findWithDetallesById(3L)).thenReturn(Optional.of(pedido));
        when(facturaRepository.findByPedidoId(3L)).thenReturn(Optional.of(factura));
        when(notaCreditoDetalleRepository.cantidadesDevueltasPorProducto(3L)).thenReturn(List.of());
        lenient().when(productoRepository.findAllForUpdate(anyCollection())).thenReturn(List.of());
        when(autonumeradorService.siguiente("NOTACREDITO")).thenReturn(1);

        assertThrows(BusinessException.class, () -> notaCreditoService.emitir(3L,
                req("Devolver de más", new ItemDevolucion(4L, 5)), "cajero@techstore.py"));
    }

    @Test
    void noSePuedeDevolverLoYaDevueltoEnUnaNcPrevia() {
        when(usuarioRepository.findByEmail("cajero@techstore.py")).thenReturn(Optional.of(cajero));
        when(pedidoRepository.findWithDetallesById(3L)).thenReturn(Optional.of(pedido));
        when(facturaRepository.findByPedidoId(3L)).thenReturn(Optional.of(factura));
        // Ya se devolvieron las 2 RAM en una NC anterior
        when(notaCreditoDetalleRepository.cantidadesDevueltasPorProducto(3L))
                .thenReturn(List.<Object[]>of(new Object[]{4L, 2L}));
        when(autonumeradorService.siguiente("NOTACREDITO")).thenReturn(2);

        assertThrows(BusinessException.class, () -> notaCreditoService.emitir(3L,
                req("Otra RAM", new ItemDevolucion(4L, 1)), "cajero@techstore.py"));
    }

    @Test
    void productoAjenoAlPedidoFalla() {
        when(usuarioRepository.findByEmail("cajero@techstore.py")).thenReturn(Optional.of(cajero));
        when(pedidoRepository.findWithDetallesById(3L)).thenReturn(Optional.of(pedido));
        when(facturaRepository.findByPedidoId(3L)).thenReturn(Optional.of(factura));
        when(notaCreditoDetalleRepository.cantidadesDevueltasPorProducto(3L)).thenReturn(List.of());
        lenient().when(productoRepository.findAllForUpdate(anyCollection())).thenReturn(List.of());
        when(autonumeradorService.siguiente("NOTACREDITO")).thenReturn(1);

        assertThrows(BusinessException.class, () -> notaCreditoService.emitir(3L,
                req("Producto que no compró", new ItemDevolucion(99L, 1)), "cajero@techstore.py"));
    }

    @Test
    void pedidoNoPagadoNoSePuedeDevolver() {
        pedido.setEstado(EstadoPedido.C);
        when(usuarioRepository.findByEmail(any())).thenReturn(Optional.of(cajero));
        when(pedidoRepository.findWithDetallesById(3L)).thenReturn(Optional.of(pedido));

        assertThrows(BusinessException.class, () -> notaCreditoService.emitir(3L,
                req("motivo", new ItemDevolucion(4L, 1)), "cajero@techstore.py"));
        verify(notaCreditoRepository, never()).save(any());
    }

    @Test
    void generaPdfDeNotaCredito() {
        NotaCredito nc = new NotaCredito();
        nc.setNumero("001-001-0000001");
        nc.setFactura(factura);
        nc.setPedido(pedido);
        nc.setMotivo("Producto fallado");
        // 800.000 × 10 ÷ 110 = 72.727,27… -> 72.727 en guaraníes enteros.
        nc.setSubtotal(Dinero.de(727_273, Moneda.PYG));
        nc.setIva(Dinero.de(72_727, Moneda.PYG));
        nc.setTotal(Dinero.de(800_000, Moneda.PYG));
        nc.setUsuario(cajero);
        com.techstore.facturacion.NotaCreditoDetalle d = new com.techstore.facturacion.NotaCreditoDetalle();
        d.setProducto(ram);
        d.setCantidad(1);
        d.setIvaAplicado(BigDecimal.TEN);
        nc.agregarDetalle(d);
        d.fijarPrecio(ram.getPrecio(), null);
        when(notaCreditoRepository.findParaPdfById(1L)).thenReturn(Optional.of(nc));

        NotaCreditoService.NotaCreditoPdf pdf = notaCreditoService.generarPdf(1L);

        org.junit.jupiter.api.Assertions.assertTrue(pdf.contenido().length > 1500);
        assertEquals("001-001-0000001", pdf.numero());
    }
}
