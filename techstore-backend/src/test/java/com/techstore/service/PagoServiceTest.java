package com.techstore.service;

import com.techstore.dto.request.RegistroPagoRequest;
import com.techstore.dto.request.RegistroPagoRequest.LineaPago;
import com.techstore.dto.response.PagoResponse;
import com.techstore.entity.EstadoFactura;
import com.techstore.entity.EstadoPedido;
import com.techstore.entity.Factura;
import com.techstore.entity.Pago;
import com.techstore.entity.Pedido;
import com.techstore.entity.PedidoDetalle;
import com.techstore.entity.Producto;
import com.techstore.entity.Rol;
import com.techstore.entity.TipoPago;
import com.techstore.entity.TipoPedido;
import com.techstore.entity.Usuario;
import com.techstore.exception.BusinessException;
import com.techstore.repository.FacturaRepository;
import com.techstore.repository.PagoRepository;
import com.techstore.repository.PedidoRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PagoServiceTest {

    @Mock
    private PagoRepository pagoRepository;

    @Mock
    private FacturaRepository facturaRepository;

    @Mock
    private PedidoRepository pedidoRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private AutonumeradorService autonumeradorService;

    @Mock
    private CajaService cajaService;

    @Mock
    private CorreoService correoService;

    @Mock
    private com.techstore.sifen.FacturaElectronicaService facturaElectronicaService;

    private final FacturaPdfService facturaPdfService = new FacturaPdfService(
            new com.techstore.config.EmpresaProperties(
                    "TechStore Paraguay", "80012345-6", "Avda. España 1234 c/ Brasil",
                    "Asunción, Paraguay", "(021) 555-123", "Venta de equipos e insumos informáticos",
                    "12345678", "01/01/2026 al 31/12/2026",
                    2, "001", "001", "47411", "11", "CENTRAL", "1", "ASUNCION", "1", "ASUNCION", "2026-01-01"));

    private PagoService pagoService;
    private Usuario cajero;
    private Pedido pedido;

    @BeforeEach
    void setUp() {
        pagoService = new PagoService(
                pagoRepository, facturaRepository, pedidoRepository, usuarioRepository,
                autonumeradorService, facturaPdfService, cajaService, correoService, facturaElectronicaService);

        Rol rol = new Rol();
        rol.setId(2L);
        rol.setNombre("Cajero");

        cajero = new Usuario();
        cajero.setId(5L);
        cajero.setEmail("cajero@techstore.py");
        cajero.setNombre("Caja");
        cajero.setApellido("Uno");
        cajero.setRol(rol);
        cajero.setActivo(true);

        Producto cpu = new Producto();
        cpu.setId(1L);
        cpu.setNombre("Intel Core i9");
        cpu.setPrecio(new BigDecimal("2500000.00"));
        cpu.setIva(BigDecimal.TEN);

        pedido = new Pedido();
        pedido.setId(10L);
        pedido.setNumero(7);
        pedido.setTipo(TipoPedido.Venta);
        pedido.setEstado(EstadoPedido.C);
        pedido.setUsuario(cajero);
        PedidoDetalle detalle = new PedidoDetalle();
        detalle.setProducto(cpu);
        detalle.setCantidad(1);
        detalle.setPrecioUnitario(cpu.getPrecio());
        detalle.setSubtotal(cpu.getPrecio());
        detalle.setIvaAplicado(BigDecimal.TEN);
        pedido.agregarDetalle(detalle);
        pedido.setTotal(new BigDecimal("2500000.00"));
    }

    private void prepararPagoFeliz() {
        when(pedidoRepository.findWithDetallesById(10L)).thenReturn(Optional.of(pedido));
        when(pagoRepository.existsByPedidoId(10L)).thenReturn(false);
        when(usuarioRepository.findByEmail("cajero@techstore.py")).thenReturn(Optional.of(cajero));
        when(autonumeradorService.siguiente("PAGO")).thenReturn(1);
        when(autonumeradorService.siguiente("FACTURA")).thenReturn(1);
        when(pagoRepository.save(any(Pago.class))).thenAnswer(inv -> inv.getArgument(0));
        when(facturaRepository.save(any(Factura.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(pedidoRepository.save(any(Pedido.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private RegistroPagoRequest todoEfectivo() {
        return new RegistroPagoRequest(List.of(
                new LineaPago(TipoPago.Efectivo, new BigDecimal("2500000.00"), null)));
    }

    @Test
    void cobroEnEfectivoEmiteFacturaConIvaDesglosado() {
        prepararPagoFeliz();

        PagoResponse response = pagoService.registrarPago(10L, todoEfectivo(), "cajero@techstore.py");

        assertEquals(7, response.pedidoNumero());
        assertEquals(new BigDecimal("2500000.00"), response.total());
        assertEquals(1, response.lineas().size());
        assertEquals("Efectivo", response.lineas().get(0).tipoPago());
        // IVA 10% incluido: 2.500.000 * 10/110 = 227.272,73
        assertEquals("001-001-0000001", response.factura().numero());
        assertEquals(new BigDecimal("227272.73"), response.factura().iva());
        assertEquals(new BigDecimal("2272727.27"), response.factura().subtotal());
        assertEquals(EstadoFactura.Pagada.name(), response.factura().estado());
        assertEquals(EstadoPedido.G, pedido.getEstado());
        verify(cajaService).registrarIngresoAutomatico(
                eq(new BigDecimal("2500000.00")), eq("Cobro pedido N.º 7 (Efectivo)"),
                eq("001-001-0000001"), eq(cajero), eq(TipoPago.Efectivo));
    }

    @Test
    void cobroMixtoRegistraDosPagosYDosMovimientos() {
        when(pedidoRepository.findWithDetallesById(10L)).thenReturn(Optional.of(pedido));
        when(pagoRepository.existsByPedidoId(10L)).thenReturn(false);
        when(usuarioRepository.findByEmail("cajero@techstore.py")).thenReturn(Optional.of(cajero));
        when(autonumeradorService.siguiente("PAGO")).thenReturn(1, 2);
        when(autonumeradorService.siguiente("FACTURA")).thenReturn(1);
        when(pagoRepository.save(any(Pago.class))).thenAnswer(inv -> inv.getArgument(0));
        when(facturaRepository.save(any(Factura.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(pedidoRepository.save(any(Pedido.class))).thenAnswer(inv -> inv.getArgument(0));

        PagoResponse response = pagoService.registrarPago(10L,
                new RegistroPagoRequest(List.of(
                        new LineaPago(TipoPago.Efectivo, new BigDecimal("1000000.00"), null),
                        new LineaPago(TipoPago.Transferencia, new BigDecimal("1500000.00"), "TX-42"))),
                "cajero@techstore.py");

        assertEquals(2, response.lineas().size());
        assertEquals(new BigDecimal("2500000.00"), response.total());
        verify(pagoRepository, times(2)).save(any(Pago.class));
        verify(cajaService).registrarIngresoAutomatico(
                eq(new BigDecimal("1000000.00")), eq("Cobro pedido N.º 7 (Efectivo)"), any(), eq(cajero),
                eq(TipoPago.Efectivo));
        verify(cajaService).registrarIngresoAutomatico(
                eq(new BigDecimal("1500000.00")), eq("Cobro pedido N.º 7 (Transferencia)"), any(), eq(cajero),
                eq(TipoPago.Transferencia));
    }

    @Test
    void cobroConSumaDistintaAlTotalFalla() {
        when(pedidoRepository.findWithDetallesById(10L)).thenReturn(Optional.of(pedido));
        when(pagoRepository.existsByPedidoId(10L)).thenReturn(false);

        assertThrows(BusinessException.class, () -> pagoService.registrarPago(10L,
                new RegistroPagoRequest(List.of(
                        new LineaPago(TipoPago.Efectivo, new BigDecimal("1000000.00"), null),
                        new LineaPago(TipoPago.Transferencia, new BigDecimal("1000000.00"), null))),
                "cajero@techstore.py"));
        verify(pagoRepository, never()).save(any());
        verify(facturaRepository, never()).save(any());
    }

    @Test
    void metodoTarjetaRechazado() {
        when(pedidoRepository.findWithDetallesById(10L)).thenReturn(Optional.of(pedido));
        when(pagoRepository.existsByPedidoId(10L)).thenReturn(false);

        assertThrows(BusinessException.class, () -> pagoService.registrarPago(10L,
                new RegistroPagoRequest(List.of(
                        new LineaPago(TipoPago.Tarjeta, new BigDecimal("2500000.00"), "VISA-1"))),
                "cajero@techstore.py"));
        verify(facturaRepository, never()).save(any());
    }

    @Test
    void metodoRepetidoFalla() {
        when(pedidoRepository.findWithDetallesById(10L)).thenReturn(Optional.of(pedido));
        when(pagoRepository.existsByPedidoId(10L)).thenReturn(false);

        assertThrows(BusinessException.class, () -> pagoService.registrarPago(10L,
                new RegistroPagoRequest(List.of(
                        new LineaPago(TipoPago.Efectivo, new BigDecimal("1250000.00"), null),
                        new LineaPago(TipoPago.Efectivo, new BigDecimal("1250000.00"), null))),
                "cajero@techstore.py"));
        verify(facturaRepository, never()).save(any());
    }

    @Test
    void cobrarPedidoNoConfirmadoFalla() {
        pedido.setEstado(EstadoPedido.P);
        when(pedidoRepository.findWithDetallesById(10L)).thenReturn(Optional.of(pedido));

        assertThrows(BusinessException.class,
                () -> pagoService.registrarPago(10L, todoEfectivo(), "cajero@techstore.py"));
        verify(pagoRepository, never()).save(any());
        verify(facturaRepository, never()).save(any());
    }

    @Test
    void pagoDuplicadoFalla() {
        when(pedidoRepository.findWithDetallesById(10L)).thenReturn(Optional.of(pedido));
        when(pagoRepository.existsByPedidoId(10L)).thenReturn(true);

        assertThrows(BusinessException.class,
                () -> pagoService.registrarPago(10L, todoEfectivo(), "cajero@techstore.py"));
        verify(facturaRepository, never()).save(any());
    }

    @Test
    void productoExentoNoGeneraIva() {
        pedido.getDetalles().get(0).setIvaAplicado(BigDecimal.ZERO);
        prepararPagoFeliz();

        PagoResponse response = pagoService.registrarPago(10L,
                new RegistroPagoRequest(List.of(
                        new LineaPago(TipoPago.Transferencia, new BigDecimal("2500000.00"), "TX-99"))),
                "cajero@techstore.py");

        assertEquals(BigDecimal.ZERO, response.factura().iva());
        assertEquals(new BigDecimal("2500000.00"), response.factura().subtotal());
    }
}
