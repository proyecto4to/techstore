package com.techstore.service;

import com.techstore.dto.request.CheckoutRequest;
import com.techstore.dto.request.CheckoutRequest.ItemCarrito;
import com.techstore.dto.response.PedidoResponse;
import com.techstore.entity.EstadoPedido;
import com.techstore.entity.Pedido;
import com.techstore.entity.PedidoDetalle;
import com.techstore.entity.Producto;
import com.techstore.entity.Rol;
import com.techstore.entity.TipoPedido;
import com.techstore.entity.Usuario;
import com.techstore.exception.BusinessException;
import com.techstore.exception.ResourceNotFoundException;
import com.techstore.mapper.PedidoMapper;
import com.techstore.mapper.PedidoMapperImpl;
import com.techstore.repository.PedidoRepository;
import com.techstore.repository.ProductoRepository;
import com.techstore.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PedidoServiceTest {

    @Mock
    private PedidoRepository pedidoRepository;

    @Mock
    private ProductoRepository productoRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private AutonumeradorService autonumeradorService;

    @Mock
    private CorreoService correoService;

    @Mock
    private EmailTemplateService emailTemplateService;

    private final PedidoMapper mapper = new PedidoMapperImpl();

    private PedidoService pedidoService;
    private Usuario cliente;
    private Producto cpu;
    private Producto gpu;

    @BeforeEach
    void setUp() {
        pedidoService = new PedidoService(
                pedidoRepository, productoRepository, usuarioRepository, autonumeradorService, mapper,
                correoService, emailTemplateService);

        Rol rol = new Rol();
        rol.setId(3L);
        rol.setNombre("Cliente");

        cliente = new Usuario();
        cliente.setId(2L);
        cliente.setEmail("cliente@test.py");
        cliente.setNombre("Cliente");
        cliente.setApellido("Prueba");
        cliente.setRol(rol);
        cliente.setActivo(true);

        cpu = producto(1L, "Intel Core i9", "2500000.00", 5, 0);
        gpu = producto(2L, "RTX 4090", "3500000.00", 2, 1);
    }

    private Producto producto(Long id, String nombre, String precio, int stockReal, int comprometido) {
        Producto p = new Producto();
        p.setId(id);
        p.setNombre(nombre);
        p.setPrecio(new BigDecimal(precio));
        p.setIva(BigDecimal.TEN);
        p.setStockReal(stockReal);
        p.setStockComprometido(comprometido);
        p.setActivo(true);
        return p;
    }

    private void prepararCheckoutFeliz() {
        when(usuarioRepository.findByEmail("cliente@test.py")).thenReturn(Optional.of(cliente));
        when(autonumeradorService.siguiente("PEDIDO")).thenReturn(1);
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void checkoutCompiledStockYCalculaTotal() {
        prepararCheckoutFeliz();
        when(productoRepository.findAllForUpdate(anyCollection())).thenReturn(List.of(cpu, gpu));

        PedidoResponse response = pedidoService.checkout("cliente@test.py",
                new CheckoutRequest(List.of(new ItemCarrito(1L, 2), new ItemCarrito(2L, 1)), "urgente"));

        assertEquals(1, response.numero());
        assertEquals(EstadoPedido.P.name(), response.estado());
        assertEquals(TipoPedido.Venta.name(), response.tipo());
        assertEquals(new BigDecimal("8500000.00"), response.total());
        assertEquals(2, response.detalles().size());
        assertEquals(2, cpu.getStockComprometido());
        assertEquals(2, gpu.getStockComprometido());
    }

    @Test
    void checkoutFusionaItemsDuplicados() {
        prepararCheckoutFeliz();
        when(productoRepository.findAllForUpdate(anyCollection())).thenReturn(List.of(cpu));

        PedidoResponse response = pedidoService.checkout("cliente@test.py",
                new CheckoutRequest(List.of(new ItemCarrito(1L, 2), new ItemCarrito(1L, 3)), null));

        assertEquals(1, response.detalles().size());
        assertEquals(5, response.detalles().get(0).cantidad());
        assertEquals(5, cpu.getStockComprometido());
    }

    @Test
    void checkoutConStockInsuficienteFalla() {
        when(usuarioRepository.findByEmail("cliente@test.py")).thenReturn(Optional.of(cliente));
        // gpu: stock real 2, comprometido 1 => disponible 1
        when(productoRepository.findAllForUpdate(anyCollection())).thenReturn(List.of(gpu));

        assertThrows(BusinessException.class, () -> pedidoService.checkout("cliente@test.py",
                new CheckoutRequest(List.of(new ItemCarrito(2L, 2)), null)));
        verify(pedidoRepository, never()).save(any());
    }

    @Test
    void checkoutConProductoInexistenteFalla() {
        when(usuarioRepository.findByEmail("cliente@test.py")).thenReturn(Optional.of(cliente));
        when(productoRepository.findAllForUpdate(anyCollection())).thenReturn(List.of());

        assertThrows(ResourceNotFoundException.class, () -> pedidoService.checkout("cliente@test.py",
                new CheckoutRequest(List.of(new ItemCarrito(99L, 1)), null)));
    }

    @Test
    void checkoutConProductoInactivoFalla() {
        cpu.setActivo(false);
        when(usuarioRepository.findByEmail("cliente@test.py")).thenReturn(Optional.of(cliente));
        when(productoRepository.findAllForUpdate(anyCollection())).thenReturn(List.of(cpu));

        assertThrows(BusinessException.class, () -> pedidoService.checkout("cliente@test.py",
                new CheckoutRequest(List.of(new ItemCarrito(1L, 1)), null)));
    }

    private Pedido pedidoPendienteDe(Usuario usuario, Producto producto, int cantidad) {
        Pedido pedido = new Pedido();
        pedido.setId(10L);
        pedido.setNumero(1);
        pedido.setTipo(TipoPedido.Venta);
        pedido.setEstado(EstadoPedido.P);
        pedido.setUsuario(usuario);
        PedidoDetalle detalle = new PedidoDetalle();
        detalle.setProducto(producto);
        detalle.setCantidad(cantidad);
        detalle.setPrecioUnitario(producto.getPrecio());
        detalle.setSubtotal(producto.getPrecio().multiply(BigDecimal.valueOf(cantidad)));
        detalle.setIvaAplicado(producto.getIva());
        pedido.agregarDetalle(detalle);
        pedido.setTotal(detalle.getSubtotal());
        return pedido;
    }

    @Test
    void confirmarDescuentaStockRealYLiberaComprometido() {
        cpu.setStockComprometido(2);
        Pedido pedido = pedidoPendienteDe(cliente, cpu, 2);
        when(pedidoRepository.findWithDetallesById(10L)).thenReturn(Optional.of(pedido));
        lenient().when(productoRepository.findAllForUpdate(anyCollection())).thenReturn(List.of(cpu));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(inv -> inv.getArgument(0));

        PedidoResponse response = pedidoService.confirmar(10L);

        assertEquals(EstadoPedido.C.name(), response.estado());
        assertEquals(3, cpu.getStockReal());
        assertEquals(0, cpu.getStockComprometido());
    }

    @Test
    void confirmarPedidoNoPendienteFalla() {
        Pedido pedido = pedidoPendienteDe(cliente, cpu, 1);
        pedido.setEstado(EstadoPedido.C);
        when(pedidoRepository.findWithDetallesById(10L)).thenReturn(Optional.of(pedido));

        assertThrows(BusinessException.class, () -> pedidoService.confirmar(10L));
    }

    @Test
    void cancelarLiberaStockComprometido() {
        cpu.setStockComprometido(2);
        Pedido pedido = pedidoPendienteDe(cliente, cpu, 2);
        when(pedidoRepository.findWithDetallesById(10L)).thenReturn(Optional.of(pedido));
        lenient().when(productoRepository.findAllForUpdate(anyCollection())).thenReturn(List.of(cpu));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(inv -> inv.getArgument(0));

        PedidoResponse response = pedidoService.cancelar(10L, "cliente@test.py", false);

        assertEquals(EstadoPedido.X.name(), response.estado());
        assertEquals(5, cpu.getStockReal());
        assertEquals(0, cpu.getStockComprometido());
    }

    @Test
    void cancelarPedidoAjenoSinSerAdminFalla() {
        Pedido pedido = pedidoPendienteDe(cliente, cpu, 1);
        when(pedidoRepository.findWithDetallesById(10L)).thenReturn(Optional.of(pedido));

        assertThrows(AccessDeniedException.class,
                () -> pedidoService.cancelar(10L, "otro@test.py", false));
    }

    @Test
    void adminPuedeCancelarPedidoAjeno() {
        cpu.setStockComprometido(1);
        Pedido pedido = pedidoPendienteDe(cliente, cpu, 1);
        when(pedidoRepository.findWithDetallesById(10L)).thenReturn(Optional.of(pedido));
        lenient().when(productoRepository.findAllForUpdate(anyCollection())).thenReturn(List.of(cpu));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(inv -> inv.getArgument(0));

        PedidoResponse response = pedidoService.cancelar(10L, "admin@techstore.py", true);

        assertEquals(EstadoPedido.X.name(), response.estado());
    }

    @Test
    void obtenerPedidoAjenoSinSerAdminFalla() {
        Pedido pedido = pedidoPendienteDe(cliente, cpu, 1);
        when(pedidoRepository.findWithDetallesById(10L)).thenReturn(Optional.of(pedido));

        assertThrows(AccessDeniedException.class,
                () -> pedidoService.obtener(10L, "otro@test.py", false));
    }
}
