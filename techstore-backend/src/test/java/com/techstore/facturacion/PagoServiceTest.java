package com.techstore.facturacion;

import com.techstore.caja.CajaService;
import com.techstore.catalogo.Producto;
import com.techstore.comun.AutonumeradorService;
import com.techstore.comun.BusinessException;
import com.techstore.comun.Dinero;
import com.techstore.comun.Moneda;
import com.techstore.empresa.EmpresaConfigService;
import com.techstore.facturacion.RegistroPagoRequest.LineaPago;
import com.techstore.notificaciones.outbox.NotificationOutboxPublisher;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;

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
    private NotificationOutboxPublisher notificationPublisher;

    @Mock
    private com.techstore.facturacion.sifen.FacturaElectronicaService facturaElectronicaService;

    private PagoService pagoService;
    private Usuario cajero;
    private Usuario cliente;
    private Pedido pedido;

    @BeforeEach
    void setUp() {
        // Emisor de prueba: establecimiento 001 y punto 001, que es el prefijo
        // que esperan las aserciones sobre el número de factura.
        EmpresaConfigService empresaConfig = FacturaPdfServiceTest.configConEmisor(
                new com.techstore.empresa.EmpresaProperties(
                        "TechStore Paraguay", "80012345-0", null, null, null, null, null, "12345678", null,
                        2, "001", "001", null, null, null, null, null, null, null, "2026-01-01", "0"));
        var puntoExpedicionService = puntoPorDefecto();
        pagoService = new PagoService(
                pagoRepository, facturaRepository, pedidoRepository, usuarioRepository,
                autonumeradorService, cajaService, notificationPublisher,
                empresaConfig, facturaElectronicaService, puntoExpedicionService);

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
        cpu.setPrecio(Dinero.de(2_500_000, Moneda.PYG));
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
        detalle.setIvaAplicado(BigDecimal.TEN);
        pedido.agregarDetalle(detalle);
        detalle.fijarPrecio(cpu.getPrecio(), null);
        pedido.setTotal(Dinero.de(2_500_000, Moneda.PYG));
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
        assertEquals(new BigDecimal("2500000"), response.total());
        assertEquals("PYG", response.moneda());
        assertEquals(1, response.lineas().size());
        assertEquals("Efectivo", response.lineas().get(0).tipoPago());
        // IVA 10% incluido: 2.500.000 × 10 ÷ 110 = 227.272,72… -> 227.273.
        // Se redondea a guaraníes enteros porque la moneda no tiene centavos; es
        // el mismo número que imprime el KuDE, que antes no coincidía con este.
        assertEquals("001-001-0000001", response.factura().numero());
        assertEquals(new BigDecimal("227273"), response.factura().iva());
        assertEquals(new BigDecimal("2272727"), response.factura().subtotal());
        assertEquals(EstadoFactura.Pagada.name(), response.factura().estado());
        assertEquals(EstadoPedido.G, pedido.getEstado());
        // "FAC" adelante: facturas y notas de crédito comparten el formato del
        // número, así que la referencia sola no alcanza para saber cuál es cuál.
        verify(cajaService).registrarIngresoAutomatico(
                eq(Dinero.de(2_500_000, Moneda.PYG)), eq("Cobro pedido N.º 7 (Efectivo)"),
                eq("FAC 001-001-0000001"), eq(cajero), eq(TipoPago.Efectivo));
    }

    @Test
    void costoDeEnvioFormaParteDelCobroYDelIvaDeLaFactura() {
        pedido.setCostoEnvio(Dinero.de(50_000, Moneda.PYG));
        pedido.setIvaEnvio(BigDecimal.TEN);
        pedido.setTotal(Dinero.de(2_550_000, Moneda.PYG));
        prepararPagoFeliz();
        RegistroPagoRequest request = new RegistroPagoRequest(List.of(
                new LineaPago(TipoPago.Efectivo, new BigDecimal("2550000"), null)), null);

        PagoResponse response = pagoService.registrarPago(10L, request, "cajero@techstore.py");

        // 227.273 del producto + 4.545 del servicio de entrega.
        assertEquals(new BigDecimal("231818"), response.factura().iva());
        assertEquals(new BigDecimal("2318182"), response.factura().subtotal());
        assertEquals(new BigDecimal("2550000"), response.factura().total());
        verify(cajaService).registrarIngresoAutomatico(
                eq(Dinero.de(2_550_000, Moneda.PYG)), any(), any(), eq(cajero), eq(TipoPago.Efectivo));
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
                        new RegistroPagoRequest.Receptor("80012345-0", "Empresa Cliente SA", null, null, null, false)),
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
                        new RegistroPagoRequest.Receptor(null, null, "mostrador@cliente.py", null, null, false)),
                "cajero@techstore.py");

        // El correo se guarda en la factura (también alimenta el dEmailRec del DE)
        // y no hace falta RUC: puede ser un consumidor final que quiere su comprobante.
        assertEquals("mostrador@cliente.py", response.factura().receptorEmail());
        assertNull(response.factura().receptorRuc());
        verify(notificationPublisher).pagoConfirmado(any(Factura.class), eq(true));
    }

    @Test
    void sinCorreoDeReceptorLaFacturaSigueYendoAlDuenoDelPedido() {
        prepararPagoFeliz();

        pagoService.registrarPago(10L, todoEfectivo(), "cajero@techstore.py");

        verify(notificationPublisher).pagoConfirmado(any(Factura.class), eq(true));
    }

    @Test
    void ventaDirectaSinReceptorNoHeredaDatosFiscalesNiCorreoDelCajero() {
        cajero.setRuc("80012345-0");
        cajero.setRazonSocial("TechStore Caja Uno");
        pedido.setUsuario(cajero);
        prepararPagoFeliz();

        PagoResponse response = pagoService.registrarPagoVentaDirecta(
                10L, todoEfectivo(), "cajero@techstore.py");

        assertNull(response.factura().receptorRuc());
        assertNull(response.factura().receptorNombre());
        assertNull(response.factura().receptorEmail());
        assertEquals("80012345-0", cajero.getRuc());
        assertEquals("TechStore Caja Uno", cajero.getRazonSocial());
        verify(usuarioRepository, never()).save(any(Usuario.class));
        verify(notificationPublisher).pagoConfirmado(any(Factura.class), eq(false));
    }

    @Test
    void ventaDirectaConReceptorNuncaActualizaLosDatosFiscalesDelCajero() {
        cajero.setRuc("RUC-DEL-CAJERO");
        cajero.setRazonSocial("Razón del cajero");
        pedido.setUsuario(cajero);
        prepararPagoFeliz();

        PagoResponse response = pagoService.registrarPagoVentaDirecta(
                10L,
                new RegistroPagoRequest(
                        List.of(new LineaPago(TipoPago.Efectivo, new BigDecimal("2500000.00"), null)),
                        new RegistroPagoRequest.Receptor(
                                "80012345-0", "Cliente de mostrador SA", null, null, null, true)),
                "cajero@techstore.py");

        assertEquals("80012345-0", response.factura().receptorRuc());
        assertEquals("Cliente de mostrador SA", response.factura().receptorNombre());
        assertEquals("RUC-DEL-CAJERO", cajero.getRuc());
        assertEquals("Razón del cajero", cajero.getRazonSocial());
        verify(usuarioRepository, never()).save(any(Usuario.class));
    }

    @Test
    void ventaDirectaConRucExigeRazonSocialExplicita() {
        pedido.setUsuario(cajero);
        when(pedidoRepository.findWithDetallesById(10L)).thenReturn(Optional.of(pedido));
        when(pagoRepository.existsByPedidoId(10L)).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                pagoService.registrarPagoVentaDirecta(
                        10L,
                        new RegistroPagoRequest(
                                List.of(new LineaPago(
                                        TipoPago.Efectivo, new BigDecimal("2500000.00"), null)),
                                new RegistroPagoRequest.Receptor(
                                        "80012345-0", "  ", null, null, null, false)),
                        "cajero@techstore.py"));

        assertEquals("facturacion.receptor.razon.social.obligatoria", ex.getClave());
        verify(pagoRepository, never()).save(any());
        verify(facturaRepository, never()).save(any());
    }

    @Test
    void ventaDirectaConCorreoExplicitoEnviaLaFacturaAlReceptor() {
        pedido.setUsuario(cajero);
        prepararPagoFeliz();

        PagoResponse response = pagoService.registrarPagoVentaDirecta(
                10L,
                new RegistroPagoRequest(
                        List.of(new LineaPago(TipoPago.Efectivo, new BigDecimal("2500000.00"), null)),
                        new RegistroPagoRequest.Receptor(
                                null, null, "mostrador@cliente.py", null, null, false)),
                "cajero@techstore.py");

        assertEquals("mostrador@cliente.py", response.factura().receptorEmail());
        assertNull(response.factura().receptorRuc());
        verify(notificationPublisher).pagoConfirmado(any(Factura.class), eq(false));
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
                        new RegistroPagoRequest.Receptor("80012345-6", null, null, null, null, false)),
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
                        new RegistroPagoRequest.Receptor("80012345-0", "Empresa Cliente SA", null, null, null, true)),
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
        assertEquals(new BigDecimal("2500000"), response.total());
        verify(pagoRepository, times(2)).save(any(Pago.class));
        verify(cajaService).registrarIngresoAutomatico(
                eq(Dinero.de(1_000_000, Moneda.PYG)), eq("Cobro pedido N.º 7 (Efectivo)"), any(), eq(cajero),
                eq(TipoPago.Efectivo));
        verify(cajaService).registrarIngresoAutomatico(
                eq(Dinero.de(1_500_000, Moneda.PYG)), eq("Cobro pedido N.º 7 (Transferencia)"), any(), eq(cajero),
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
    void clienteListaSolamenteLasFacturasDeSusPedidos() {
        Pageable pageable = Pageable.ofSize(10);
        when(facturaRepository.findByPedidoUsuarioEmail(cliente.getEmail(), pageable))
                .thenReturn(Page.empty(pageable));

        var response = pagoService.listarFacturas(cliente.getEmail(), false, pageable);

        assertEquals(0, response.totalElements());
        verify(facturaRepository).findByPedidoUsuarioEmail(cliente.getEmail(), pageable);
        verify(facturaRepository, never()).findAll(pageable);
    }

    @Test
    void staffListaTodasLasFacturas() {
        Pageable pageable = Pageable.ofSize(10);
        when(facturaRepository.findAll(pageable)).thenReturn(Page.empty(pageable));

        var response = pagoService.listarFacturas(cajero.getEmail(), true, pageable);

        assertEquals(0, response.totalElements());
        verify(facturaRepository).findAll(pageable);
        verify(facturaRepository, never()).findByPedidoUsuarioEmail(any(), any());
    }

    @Test
    void clienteNoPuedeConsultarNiDescargarUnaFacturaAjena() {
        Factura factura = new Factura();
        pedido.setUsuario(cliente);
        pedido.setEstado(EstadoPedido.G);
        factura.setPedido(pedido);
        factura.setEstado(EstadoFactura.Pagada);
        when(facturaRepository.findWithPedidoById(33L)).thenReturn(Optional.of(factura));
        when(pagoRepository.existsByPedidoId(pedido.getId())).thenReturn(true);

        assertThrows(AccessDeniedException.class,
                () -> pagoService.obtenerFactura(33L, "otro@techstore.py", false));
        assertThrows(AccessDeniedException.class,
                () -> pagoService.generarPdf(33L, "otro@techstore.py", false));

        verify(facturaElectronicaService, never()).generarKude(33L);
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
        assertEquals(new BigDecimal("2500000"), response.factura().subtotal());
    }
    /**
     * Un servicio de puntos de expedición que siempre devuelve el punto por
     * defecto 001-001, que es el que usaban las facturas antes de que hubiera
     * varios. Así los números esperados en estos tests no cambian.
     */
    private static com.techstore.empresa.PuntoExpedicionService puntoPorDefecto() {
        com.techstore.empresa.PuntoExpedicion punto = new com.techstore.empresa.PuntoExpedicion();
        punto.setEstablecimiento("001");
        punto.setPunto("001");
        punto.setPorDefecto(true);
        punto.setActivo(true);
        var servicio = org.mockito.Mockito.mock(com.techstore.empresa.PuntoExpedicionService.class);
        org.mockito.Mockito.lenient()
                .when(servicio.delUsuario(org.mockito.ArgumentMatchers.any())).thenReturn(punto);
        return servicio;
    }
}
