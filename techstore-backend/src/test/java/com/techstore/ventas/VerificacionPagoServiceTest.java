package com.techstore.ventas;

import com.techstore.comun.BusinessException;
import com.techstore.comun.Dinero;
import com.techstore.comun.Moneda;
import com.techstore.facturacion.PagoService;
import com.techstore.notificaciones.outbox.NotificationOutboxPublisher;
import com.techstore.usuarios.Usuario;
import com.techstore.usuarios.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VerificacionPagoServiceTest {

    @Mock
    private PedidoRepository pedidoRepository;

    @Mock
    private PagoComprobanteRepository comprobanteRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private PedidoService pedidoService;

    @Mock
    private PagoService pagoService;

    @Mock
    private PedidoHistorial historial;

    @Mock
    private NotificationOutboxPublisher notificaciones;

    private VerificacionPagoService servicio;
    private Usuario admin;
    private Pedido pedido;
    private PagoComprobante comprobante;

    @BeforeEach
    void setUp() {
        servicio = new VerificacionPagoService(pedidoRepository, comprobanteRepository, usuarioRepository,
                pedidoService, pagoService, historial, notificaciones);

        admin = new Usuario();
        admin.setId(1L);
        admin.setEmail("admin@techstore.py");

        Usuario cliente = new Usuario();
        cliente.setId(2L);
        cliente.setEmail("cliente@test.py");

        pedido = new Pedido();
        pedido.setId(10L);
        pedido.setNumero(80);
        pedido.setTipo(TipoPedido.Venta);
        pedido.setEstado(EstadoPedido.P);
        pedido.setUsuario(cliente);
        pedido.setMetodoEntrega(MetodoEntrega.ENVIO_DOMICILIO);
        pedido.setMetodoPago(MetodoPagoCheckout.TRANSFERENCIA_BANCARIA);
        pedido.setEstadoPago(EstadoPago.EN_VERIFICACION);
        pedido.setTotal(Dinero.de(new BigDecimal("140000"), Moneda.PYG));

        comprobante = new PagoComprobante();
        comprobante.setId(55L);
        comprobante.setPedido(pedido);
        comprobante.setEstado(EstadoComprobante.EN_REVISION);

        lenient().when(usuarioRepository.findByEmail("admin@techstore.py")).thenReturn(Optional.of(admin));
        lenient().when(pedidoRepository.findWithDetallesForUpdateById(10L)).thenReturn(Optional.of(pedido));
        lenient().when(comprobanteRepository.findByPedidoIdAndEstado(10L, EstadoComprobante.EN_REVISION))
                .thenReturn(List.of(comprobante));
    }

    @Test
    void aprobarDejaElPagoAprobadoConfirmaYCobraPorTransferencia() {
        servicio.aprobar(10L, new AprobacionPagoRequest(" 000123 "), "admin@techstore.py");

        assertEquals(EstadoPago.APROBADO, pedido.getEstadoPago());
        assertEquals(EstadoComprobante.APROBADO, comprobante.getEstado());
        assertEquals(admin, pedido.getPagoVerificadoPor());
        assertNotNull(pedido.getPagoVerificadoEn());
        // El pago se aprueba antes de confirmar: es lo que habilita confirmar un
        // pedido con pago previo.
        InOrder orden = inOrder(pedidoRepository, pedidoService, pagoService);
        orden.verify(pedidoRepository).save(pedido);
        orden.verify(pedidoService).confirmar(10L, "admin@techstore.py");
        orden.verify(pagoService).registrarPagoTransferenciaVerificada(10L, "000123", "admin@techstore.py");
        verify(historial).registrar(eq(pedido), eq(TipoEventoPedido.PAGO_APROBADO), anyString(), eq(admin));
    }

    @Test
    void aprobarUnPedidoYaConfirmadoNoLoVuelveAConfirmar() {
        pedido.setEstado(EstadoPedido.C);

        servicio.aprobar(10L, null, "admin@techstore.py");

        verify(pedidoService, never()).confirmar(anyLong(), anyString());
        verify(pagoService).registrarPagoTransferenciaVerificada(10L, null, "admin@techstore.py");
    }

    @Test
    void noSeApruebaUnPagoQueNoEstaEnVerificacion() {
        pedido.setEstadoPago(EstadoPago.APROBADO);

        BusinessException error = assertThrows(BusinessException.class,
                () -> servicio.aprobar(10L, null, "admin@techstore.py"));

        assertEquals("pagos.verificacion.no.pendiente", error.getClave());
        verify(pedidoService, never()).confirmar(anyLong(), anyString());
        verify(pagoService, never()).registrarPagoTransferenciaVerificada(anyLong(), any(), anyString());
    }

    @Test
    void noSeApruebaElPagoDeUnPedidoCancelado() {
        pedido.setEstado(EstadoPedido.X);

        BusinessException error = assertThrows(BusinessException.class,
                () -> servicio.aprobar(10L, null, "admin@techstore.py"));

        assertEquals("pagos.verificacion.estado.pedido", error.getClave());
        assertEquals(EstadoPago.EN_VERIFICACION, pedido.getEstadoPago());
    }

    @Test
    void rechazarGuardaElMotivoYAvisaAlClienteSinTocarElStock() {
        servicio.rechazar(10L, new RechazoPagoRequest("  El monto no coincide con el total  "), "admin@techstore.py");

        assertEquals(EstadoPago.RECHAZADO, pedido.getEstadoPago());
        assertEquals("El monto no coincide con el total", pedido.getPagoRechazoMotivo());
        assertEquals(EstadoComprobante.RECHAZADO, comprobante.getEstado());
        assertEquals("El monto no coincide con el total", comprobante.getMotivoRechazo());
        assertEquals(EstadoPedido.P, pedido.getEstado());
        verify(notificaciones).pagoRechazado(pedido, 55L);
        verify(pedidoService, never()).confirmar(anyLong(), anyString());
        verify(pagoService, never()).registrarPagoTransferenciaVerificada(anyLong(), any(), anyString());
    }

    @Test
    void aprobarDespuesDeUnRechazoLimpiaElMotivo() {
        pedido.setPagoRechazoMotivo("La foto no se lee");

        servicio.aprobar(10L, null, "admin@techstore.py");

        assertNull(pedido.getPagoRechazoMotivo());
    }
}
