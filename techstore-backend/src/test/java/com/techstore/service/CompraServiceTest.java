package com.techstore.service;

import com.techstore.dto.request.CompraRequest;
import com.techstore.dto.request.CompraRequest.ItemCompra;
import com.techstore.dto.response.CompraResumenResponse;
import com.techstore.entity.EstadoPedido;
import com.techstore.entity.Pedido;
import com.techstore.entity.PedidoDetalle;
import com.techstore.entity.Producto;
import com.techstore.entity.Proveedor;
import com.techstore.entity.Rol;
import com.techstore.entity.TipoPago;
import com.techstore.entity.TipoPedido;
import com.techstore.entity.Usuario;
import com.techstore.exception.BusinessException;
import com.techstore.repository.PedidoRepository;
import com.techstore.repository.ProductoRepository;
import com.techstore.repository.ProveedorRepository;
import com.techstore.repository.UsuarioRepository;
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

    private CompraService compraService;
    private Usuario admin;
    private Proveedor proveedor;
    private Producto gpu;

    @BeforeEach
    void setUp() {
        compraService = new CompraService(
                pedidoRepository, productoRepository, proveedorRepository, usuarioRepository,
                autonumeradorService, cajaService);

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
        gpu.setPrecio(new BigDecimal("3500000.00"));
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
        detalle.setPrecioUnitario(new BigDecimal("2800000"));
        detalle.setSubtotal(new BigDecimal("14000000"));
        detalle.setIvaAplicado(BigDecimal.TEN);
        pedido.agregarDetalle(detalle);
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

    @Test
    void pagarRegistraEgresoConMedioYMarcaPagada() {
        Pedido pedido = compraPendiente();
        when(pedidoRepository.findWithDetallesById(20L)).thenReturn(Optional.of(pedido));
        when(usuarioRepository.findByEmail("admin@techstore.py")).thenReturn(Optional.of(admin));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(inv -> inv.getArgument(0));

        CompraResumenResponse compra = compraService.pagar(20L, TipoPago.Transferencia, "admin@techstore.py");

        assertTrue(compra.pagada());
        // El pago al proveedor sale como egreso de caja con el medio elegido.
        verify(cajaService).registrarEgresoAutomatico(
                eq(new BigDecimal("14000000")), any(), eq("COMPRA-10"), eq(admin), eq(TipoPago.Transferencia));
    }

    @Test
    void pagarUnaCompraYaPagadaFalla() {
        Pedido pedido = compraPendiente();
        pedido.setPagada(true);
        when(pedidoRepository.findWithDetallesById(20L)).thenReturn(Optional.of(pedido));

        assertThrows(BusinessException.class,
                () -> compraService.pagar(20L, TipoPago.Efectivo, "admin@techstore.py"));
        verify(cajaService, never()).registrarEgresoAutomatico(any(), any(), any(), any(), any());
    }
}
