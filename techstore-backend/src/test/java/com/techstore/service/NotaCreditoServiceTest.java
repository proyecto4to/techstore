package com.techstore.service;

import com.techstore.config.EmpresaProperties;
import com.techstore.dto.response.NotaCreditoResponse;
import com.techstore.entity.EstadoFactura;
import com.techstore.entity.EstadoPedido;
import com.techstore.entity.Factura;
import com.techstore.entity.NotaCredito;
import com.techstore.entity.Pedido;
import com.techstore.entity.PedidoDetalle;
import com.techstore.entity.Producto;
import com.techstore.entity.Rol;
import com.techstore.entity.TipoPedido;
import com.techstore.entity.Usuario;
import com.techstore.exception.BusinessException;
import com.techstore.repository.FacturaRepository;
import com.techstore.repository.NotaCreditoRepository;
import com.techstore.repository.PedidoRepository;
import com.techstore.repository.ProductoRepository;
import com.techstore.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
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

    private final FacturaPdfService facturaPdfService = new FacturaPdfService(new EmpresaProperties(
            "TechStore Paraguay", "80012345-6", "Avda. España 1234 c/ Brasil", "Asunción, Paraguay",
            "(021) 555-123", "Venta de equipos e insumos informáticos", "12345678",
            "01/01/2026 al 31/12/2026"));

    private NotaCreditoService notaCreditoService;
    private Usuario cajero;
    private Pedido pedido;
    private Factura factura;
    private Producto ram;

    @BeforeEach
    void setUp() {
        notaCreditoService = new NotaCreditoService(
                notaCreditoRepository, pedidoRepository, facturaRepository, productoRepository,
                usuarioRepository, autonumeradorService, cajaService, facturaPdfService);

        Rol rol = new Rol();
        rol.setNombre("Cajero");
        cajero = new Usuario();
        cajero.setId(5L);
        cajero.setEmail("cajero@techstore.py");
        cajero.setNombre("Caja");
        cajero.setApellido("Uno");
        cajero.setRol(rol);

        ram = new Producto();
        ram.setId(4L);
        ram.setNombre("Kingston 64GB RAM");
        ram.setPrecio(new BigDecimal("800000.00"));
        ram.setIva(BigDecimal.TEN);
        ram.setStockReal(9);
        ram.setStockComprometido(0);

        pedido = new Pedido();
        pedido.setId(3L);
        pedido.setNumero(3);
        pedido.setTipo(TipoPedido.Venta);
        pedido.setEstado(EstadoPedido.G);
        pedido.setUsuario(cajero);
        PedidoDetalle detalle = new PedidoDetalle();
        detalle.setProducto(ram);
        detalle.setCantidad(1);
        detalle.setPrecioUnitario(ram.getPrecio());
        detalle.setSubtotal(ram.getPrecio());
        detalle.setIvaAplicado(BigDecimal.TEN);
        pedido.agregarDetalle(detalle);
        pedido.setTotal(ram.getPrecio());

        factura = new Factura();
        factura.setId(7L);
        factura.setNumero("001-001-0000002");
        factura.setTipo("CONTADO");
        factura.setPedido(pedido);
        factura.setSubtotal(new BigDecimal("727272.73"));
        factura.setIva(new BigDecimal("72727.27"));
        factura.setTotal(new BigDecimal("800000.00"));
        factura.setEstado(EstadoFactura.Pagada);
        factura.setUsuario(cajero);
    }

    @Test
    void emitirDevuelveStockAnulaFacturaYRegistraEgreso() {
        when(usuarioRepository.findByEmail("cajero@techstore.py")).thenReturn(Optional.of(cajero));
        when(pedidoRepository.findWithDetallesById(3L)).thenReturn(Optional.of(pedido));
        when(facturaRepository.findByPedidoId(3L)).thenReturn(Optional.of(factura));
        when(notaCreditoRepository.existsByFacturaId(7L)).thenReturn(false);
        lenient().when(productoRepository.findAllForUpdate(anyCollection())).thenReturn(java.util.List.of(ram));
        when(autonumeradorService.siguiente("NOTACREDITO")).thenReturn(1);
        when(notaCreditoRepository.save(any(NotaCredito.class))).thenAnswer(inv -> inv.getArgument(0));
        when(facturaRepository.save(any(Factura.class))).thenAnswer(inv -> inv.getArgument(0));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(inv -> inv.getArgument(0));

        NotaCreditoResponse nc = notaCreditoService.emitir(3L, "Producto fallado", "cajero@techstore.py");

        assertEquals("001-001-0000001", nc.numero());
        assertEquals("001-001-0000002", nc.facturaNumero());
        assertEquals(new BigDecimal("800000.00"), nc.total());
        assertEquals(10, ram.getStockReal()); // 9 + 1 devuelta
        assertEquals(EstadoFactura.Anulada, factura.getEstado());
        assertEquals(EstadoPedido.R, pedido.getEstado());
        verify(cajaService).registrarEgresoAutomatico(
                eq(new BigDecimal("800000.00")), eq("Devolución pedido N.º 3"),
                eq("001-001-0000001"), eq(cajero));
    }

    @Test
    void pedidoNoPagadoNoSePuedeDevolver() {
        pedido.setEstado(EstadoPedido.C);
        when(usuarioRepository.findByEmail(any())).thenReturn(Optional.of(cajero));
        when(pedidoRepository.findWithDetallesById(3L)).thenReturn(Optional.of(pedido));

        assertThrows(BusinessException.class,
                () -> notaCreditoService.emitir(3L, "motivo", "cajero@techstore.py"));
        verify(notaCreditoRepository, never()).save(any());
    }

    @Test
    void facturaConNotaDeCreditoPreviaFalla() {
        when(usuarioRepository.findByEmail(any())).thenReturn(Optional.of(cajero));
        when(pedidoRepository.findWithDetallesById(3L)).thenReturn(Optional.of(pedido));
        when(facturaRepository.findByPedidoId(3L)).thenReturn(Optional.of(factura));
        when(notaCreditoRepository.existsByFacturaId(7L)).thenReturn(true);

        assertThrows(BusinessException.class,
                () -> notaCreditoService.emitir(3L, "motivo", "cajero@techstore.py"));
    }

    @Test
    void generaPdfDeNotaCredito() {
        NotaCredito nc = new NotaCredito();
        nc.setNumero("001-001-0000001");
        nc.setFactura(factura);
        nc.setPedido(pedido);
        nc.setMotivo("Producto fallado");
        nc.setSubtotal(factura.getSubtotal());
        nc.setIva(factura.getIva());
        nc.setTotal(factura.getTotal());
        nc.setUsuario(cajero);
        when(notaCreditoRepository.findParaPdfById(1L)).thenReturn(Optional.of(nc));

        NotaCreditoService.NotaCreditoPdf pdf = notaCreditoService.generarPdf(1L);

        org.junit.jupiter.api.Assertions.assertTrue(pdf.contenido().length > 1500);
        assertEquals("001-001-0000001", pdf.numero());
    }
}
