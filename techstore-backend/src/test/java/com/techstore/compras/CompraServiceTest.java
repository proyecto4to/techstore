package com.techstore.compras;

import com.techstore.auditoria.AuditoriaService;
import com.techstore.caja.CajaService;
import com.techstore.catalogo.Producto;
import com.techstore.catalogo.ProductoRepository;
import com.techstore.compras.CompraRequest.ItemCompra;
import com.techstore.comun.AutonumeradorService;
import com.techstore.comun.BusinessException;
import com.techstore.comun.Dinero;
import com.techstore.comun.Moneda;
import com.techstore.facturacion.RegistroPagoRequest.LineaPago;
import com.techstore.facturacion.TipoPago;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompraServiceTest {

    @Mock
    private PedidoRepository pedidoRepository;

    @Mock
    private ProductoRepository productoRepository;

    @Mock
    private ProveedorRepository proveedorRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private AutonumeradorService autonumeradorService;

    @Mock
    private CajaService cajaService;

    @Mock
    private AuditoriaService auditoriaService;

    private CompraService compraService;
    private Usuario admin;
    private Proveedor proveedor;
    private Producto gpu;

    @BeforeEach
    void setUp() {
        compraService = new CompraService(
                pedidoRepository, productoRepository, proveedorRepository, usuarioRepository,
                autonumeradorService, cajaService, auditoriaService);

        Rol rol = new Rol();
        rol.setNombre("Admin");
        admin = new Usuario();
        admin.setId(1L);
        admin.setEmail("admin@techstore.py");
        admin.setNombre("Admin");
        admin.setApellido("Sistema");
        admin.setRol(rol);

        proveedor = new Proveedor();
        proveedor.setId(3L);
        proveedor.setRazonSocial("Distribuidora Tech SA");
        proveedor.setActivo(true);

        gpu = new Producto();
        gpu.setId(2L);
        gpu.setNombre("NVIDIA RTX 4090");
        gpu.setPrecio(Dinero.de(3_500_000, Moneda.PYG));
        gpu.setIva(BigDecimal.TEN);
        gpu.setStockReal(1);
        gpu.setStockComprometido(0);
    }

    @Test
    void creaCompraPendienteSinTocarStock() {
        when(usuarioRepository.findByEmail("admin@techstore.py")).thenReturn(Optional.of(admin));
        when(proveedorRepository.findById(3L)).thenReturn(Optional.of(proveedor));
        when(productoRepository.findAllById(any())).thenReturn(List.of(gpu));
        when(autonumeradorService.siguiente("PEDIDO")).thenReturn(10);
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(inv -> inv.getArgument(0));

        CompraResumenResponse compra = compraService.crear(
                new CompraRequest(3L, List.of(new ItemCompra(2L, 5, new BigDecimal("2800000"))), "reposición"),
                "admin@techstore.py");

        assertEquals(10, compra.numero());
        assertEquals("P", compra.estado());
        assertEquals(new BigDecimal("14000000"), compra.total());
        assertEquals("Distribuidora Tech SA", compra.proveedorRazonSocial());
        assertEquals(1, gpu.getStockReal()); // el stock no cambia hasta recibir
    }

    @Test
    void proveedorInactivoFalla() {
        proveedor.setActivo(false);
        when(usuarioRepository.findByEmail(any())).thenReturn(Optional.of(admin));
        when(proveedorRepository.findById(3L)).thenReturn(Optional.of(proveedor));

        assertThrows(BusinessException.class, () -> compraService.crear(
                new CompraRequest(3L, List.of(new ItemCompra(2L, 1, BigDecimal.TEN)), null),
                "admin@techstore.py"));
    }

    private Pedido compraPendiente() {
        Pedido pedido = new Pedido();
        pedido.setId(20L);
        pedido.setNumero(10);
        pedido.setTipo(TipoPedido.Compra);
        pedido.setEstado(EstadoPedido.P);
        pedido.setUsuario(admin);
        pedido.setProveedor(proveedor);
        PedidoDetalle detalle = new PedidoDetalle();
        detalle.setProducto(gpu);
        detalle.setCantidad(5);
        detalle.setIvaAplicado(BigDecimal.TEN);
        pedido.agregarDetalle(detalle);
        detalle.fijarPrecio(Dinero.de(2_800_000, Moneda.PYG), null);
        pedido.setTotal(detalle.getSubtotal());
        return pedido;
    }

    @Test
    void recibirSumaStockYConfirma() {
        Pedido pedido = compraPendiente();
        when(pedidoRepository.findWithDetallesById(20L)).thenReturn(Optional.of(pedido));
        lenient().when(productoRepository.findAllForUpdate(anyCollection())).thenReturn(List.of(gpu));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(inv -> inv.getArgument(0));

        CompraResumenResponse compra = compraService.recibir(20L);

        assertEquals("C", compra.estado());
        assertEquals(6, gpu.getStockReal()); // 1 + 5 recibidas
        // Recibir NO mueve la caja: el egreso ocurre al pagar.
        verifyNoInteractions(cajaService);
    }

    @Test
    void recibirCompraYaRecibidaFalla() {
        Pedido pedido = compraPendiente();
        pedido.setEstado(EstadoPedido.C);
        when(pedidoRepository.findWithDetallesById(20L)).thenReturn(Optional.of(pedido));

        assertThrows(BusinessException.class, () -> compraService.recibir(20L));
    }

    @Test
    void unaVentaNoSePuedeRecibirComoCompra() {
        Pedido pedido = compraPendiente();
        pedido.setTipo(TipoPedido.Venta);
        when(pedidoRepository.findWithDetallesById(20L)).thenReturn(Optional.of(pedido));

        assertThrows(BusinessException.class, () -> compraService.recibir(20L));
    }

    private static List<LineaPago> linea(TipoPago medio, String monto) {
        return List.of(new LineaPago(medio, new BigDecimal(monto), null));
    }

    @Test
    void pagarRegistraEgresoConMedioYMarcaPagada() {
        Pedido pedido = compraPendiente();
        when(pedidoRepository.findWithDetallesById(20L)).thenReturn(Optional.of(pedido));
        when(usuarioRepository.findByEmail("admin@techstore.py")).thenReturn(Optional.of(admin));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(inv -> inv.getArgument(0));

        CompraResumenResponse compra = compraService.pagar(
                20L, linea(TipoPago.Transferencia, "14000000"), "admin@techstore.py");

        assertTrue(compra.pagada());
        // El pago al proveedor sale como egreso de caja con el medio elegido.
        verify(cajaService).registrarEgresoAutomatico(
                eq(Dinero.de(14_000_000, Moneda.PYG)), any(), eq("COMPRA-10"), eq(admin),
                eq(TipoPago.Transferencia));
    }

    @Test
    void pagoMixtoRegistraUnEgresoPorCadaMedio() {
        Pedido pedido = compraPendiente();
        when(pedidoRepository.findWithDetallesById(20L)).thenReturn(Optional.of(pedido));
        when(usuarioRepository.findByEmail("admin@techstore.py")).thenReturn(Optional.of(admin));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(inv -> inv.getArgument(0));

        compraService.pagar(20L, List.of(
                new LineaPago(TipoPago.Efectivo, new BigDecimal("4000000"), null),
                new LineaPago(TipoPago.Transferencia, new BigDecimal("10000000"), "TX-7")),
                "admin@techstore.py");

        // Dos egresos separados: el cierre concilia el cajón aparte del banco.
        verify(cajaService).registrarEgresoAutomatico(
                eq(Dinero.de(4_000_000, Moneda.PYG)), any(), eq("COMPRA-10"), eq(admin), eq(TipoPago.Efectivo));
        verify(cajaService).registrarEgresoAutomatico(
                eq(Dinero.de(10_000_000, Moneda.PYG)), any(), eq("COMPRA-10"), eq(admin),
                eq(TipoPago.Transferencia));
    }

    @Test
    void siLasLineasNoSumanElTotalNoSePaga() {
        Pedido pedido = compraPendiente();
        when(pedidoRepository.findWithDetallesById(20L)).thenReturn(Optional.of(pedido));

        assertThrows(BusinessException.class, () -> compraService.pagar(
                20L, linea(TipoPago.Efectivo, "1000000"), "admin@techstore.py"));
        verify(cajaService, never()).registrarEgresoAutomatico(any(), any(), any(), any(), any());
    }

    @Test
    void noSeAceptaDosVecesElMismoMedio() {
        Pedido pedido = compraPendiente();
        when(pedidoRepository.findWithDetallesById(20L)).thenReturn(Optional.of(pedido));

        assertThrows(BusinessException.class, () -> compraService.pagar(20L, List.of(
                new LineaPago(TipoPago.Efectivo, new BigDecimal("7000000"), null),
                new LineaPago(TipoPago.Efectivo, new BigDecimal("7000000"), null)),
                "admin@techstore.py"));
        verify(cajaService, never()).registrarEgresoAutomatico(any(), any(), any(), any(), any());
    }

    @Test
    void pagarUnaCompraYaPagadaFalla() {
        Pedido pedido = compraPendiente();
        pedido.setPagada(true);
        when(pedidoRepository.findWithDetallesById(20L)).thenReturn(Optional.of(pedido));

        assertThrows(BusinessException.class,
                () -> compraService.pagar(20L, linea(TipoPago.Efectivo, "14000000"), "admin@techstore.py"));
        verify(cajaService, never()).registrarEgresoAutomatico(any(), any(), any(), any(), any());
    }
}
