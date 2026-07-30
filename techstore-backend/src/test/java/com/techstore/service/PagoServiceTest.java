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
import com.techstore.entity.TipoCorreo;
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
import static org.junit.jupiter.api.Assertions.assertNull;
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
    private EmailTemplateService emailTemplateService;

    @Mock
    private com.techstore.sifen.FacturaElectronicaService facturaElectronicaService;

    private PagoService pagoService;
    private Usuario cajero;
    private Usuario cliente;
    private Pedido pedido;

    @BeforeEach
    void setUp() {
        // Emisor de prueba: establecimiento 001 y punto 001, que es el prefijo
        // que esperan las aserciones sobre el número de factura.
        EmpresaConfigService empresaConfig = FacturaPdfServiceTest.configConEmisor(
                new com.techstore.config.EmpresaProperties(
                        "TechStore Paraguay", "80012345-0", null, null, null, null, "12345678", null,
                        2, "001", "001", null, null, null, null, null, null, null, "2026-01-01"));
        pagoService = new PagoService(
                pagoRepository, facturaRepository, pedidoRepository, usuarioRepository,
                autonumeradorService, cajaService, correoService, emailTemplateService,
                empresaConfig, facturaElectronicaService);

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

        Rol rolCliente = new Rol();
        rolCliente.setId(3L);
        rolCliente.setNombre("Cliente");

        cliente = new Usuario();
        cliente.setId(9L);
        cliente.setEmail("cliente@techstore.py");
        cliente.setNombre("Ana");
        cliente.setApellido("López");
        cliente.setRol(rolCliente);
        cliente.setActivo(true);

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
        pedido.setUsuario(cliente);
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
                new LineaPago(TipoPago.Efectivo, new BigDecimal("2500000.00"), null)), null);
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
    void sinReceptorLaFacturaSaleAConsumidorFinal() {
        prepararPagoFeliz();

        PagoResponse response = pagoService.registrarPago(10L, todoEfectivo(), "cajero@techstore.py");

        assertNull(response.factura().receptorRuc());
    }

    @Test
    void conRucLaFacturaSeEmiteAEseContribuyente() {
        prepararPagoFeliz();

        PagoResponse response = pagoService.registrarPago(
                10L,
                new RegistroPagoRequest(
                        List.of(new LineaPago(TipoPago.Efectivo, new BigDecimal("2500000.00"), null)),
                        new RegistroPagoRequest.Receptor("80012345-0", "Empresa Cliente SA", null, false)),
                "cajero@techstore.py");

        assertEquals("80012345-0", response.factura().receptorRuc());
        assertEquals("Empresa Cliente SA", response.factura().receptorNombre());
        // No se pidió guardarlo, así que la cuenta del cliente queda intacta.
        assertNull(cliente.getRuc());
    }

    @Test
    void conCorreoDeReceptorLaFacturaSeMandaAlClienteYNoAlDuenoDelPedido() {
        prepararPagoFeliz();

        PagoResponse response = pagoService.registrarPago(
                10L,
                new RegistroPagoRequest(
                        List.of(new LineaPago(TipoPago.Efectivo, new BigDecimal("2500000.00"), null)),
                        new RegistroPagoRequest.Receptor(null, null, "mostrador@cliente.py", false)),
                "cajero@techstore.py");

        // El correo se guarda en la factura (también alimenta el dEmailRec del DE)
        // y no hace falta RUC: puede ser un consumidor final que quiere su comprobante.
        assertEquals("mostrador@cliente.py", response.factura().receptorEmail());
        assertNull(response.factura().receptorRuc());
        verify(correoService).enviar(
                eq(TipoCorreo.FACTURA), eq("mostrador@cliente.py"), any(), any(), any(), any(), any(), any());
    }

    @Test
    void sinCorreoDeReceptorLaFacturaSigueYendoAlDuenoDelPedido() {
        prepararPagoFeliz();

        pagoService.registrarPago(10L, todoEfectivo(), "cajero@techstore.py");

        verify(correoService).enviar(
                eq(TipoCorreo.FACTURA), eq("cliente@techstore.py"), any(), any(), any(), any(), any(), any());
    }

    @Test
    void seRechazaUnRucConDigitoVerificadorInvalido() {
        when(pedidoRepository.findWithDetallesById(10L)).thenReturn(Optional.of(pedido));
        when(pagoRepository.existsByPedidoId(10L)).thenReturn(false);
        when(usuarioRepository.findByEmail("cajero@techstore.py")).thenReturn(Optional.of(cajero));
        when(autonumeradorService.siguiente("PAGO")).thenReturn(1);
        when(autonumeradorService.siguiente("FACTURA")).thenReturn(1);

        assertThrows(BusinessException.class, () -> pagoService.registrarPago(
                10L,
                new RegistroPagoRequest(
                        List.of(new LineaPago(TipoPago.Efectivo, new BigDecimal("2500000.00"), null)),
                        new RegistroPagoRequest.Receptor("80012345-6", null, null, false)),
                "cajero@techstore.py"));
        verify(facturaRepository, never()).save(any());
    }

    @Test
    void guardarEnClienteDejaElRucCargadoParaLaProximaCompra() {
        prepararPagoFeliz();

        pagoService.registrarPago(
                10L,
                new RegistroPagoRequest(
                        List.of(new LineaPago(TipoPago.Efectivo, new BigDecimal("2500000.00"), null)),
                        new RegistroPagoRequest.Receptor("80012345-0", "Empresa Cliente SA", null, true)),
                "cajero@techstore.py");

        assertEquals("80012345-0", cliente.getRuc());
        assertEquals("Empresa Cliente SA", cliente.getRazonSocial());
        verify(usuarioRepository).save(cliente);
    }

    @Test
    void siElClienteTieneRucCargadoSeFacturaAEseRucSinPedirlo() {
        cliente.setRuc("80012345-0");
        cliente.setRazonSocial("Empresa Cliente SA");
        prepararPagoFeliz();

        PagoResponse response = pagoService.registrarPago(10L, todoEfectivo(), "cajero@techstore.py");

        assertEquals("80012345-0", response.factura().receptorRuc());
        assertEquals("Empresa Cliente SA", response.factura().receptorNombre());
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
                        new LineaPago(TipoPago.Transferencia, new BigDecimal("1500000.00"), "TX-42")), null),
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
                        new LineaPago(TipoPago.Transferencia, new BigDecimal("1000000.00"), null)), null),
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
                        new LineaPago(TipoPago.Tarjeta, new BigDecimal("2500000.00"), "VISA-1")), null),
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
                        new LineaPago(TipoPago.Efectivo, new BigDecimal("1250000.00"), null)), null),
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
                        new LineaPago(TipoPago.Transferencia, new BigDecimal("2500000.00"), "TX-99")), null),
                "cajero@techstore.py");

        assertEquals(BigDecimal.ZERO, response.factura().iva());
        assertEquals(new BigDecimal("2500000.00"), response.factura().subtotal());
    }
}
