package com.techstore.ventas;

import com.techstore.catalogo.Producto;
import com.techstore.catalogo.ProductoRepository;
import com.techstore.comun.AutonumeradorService;
import com.techstore.comun.BusinessException;
import com.techstore.comun.Dinero;
import com.techstore.comun.Moneda;
import com.techstore.facturacion.PagoService;
import com.techstore.facturacion.RegistroPagoRequest.LineaPago;
import com.techstore.facturacion.RegistroPagoRequest;
import com.techstore.facturacion.TipoPago;
import com.techstore.usuarios.Rol;
import com.techstore.usuarios.Usuario;
import com.techstore.usuarios.UsuarioRepository;
import com.techstore.ventas.VentaDirectaRequest.ItemVenta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VentaDirectaServiceTest {

    @Mock
    private PedidoRepository pedidoRepository;

    @Mock
    private ProductoRepository productoRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private AutonumeradorService autonumeradorService;

    @Mock
    private PagoService pagoService;

    private VentaDirectaService ventaDirectaService;
    private Usuario cajero;
    private Producto cable;

    @BeforeEach
    void setUp() {
        ventaDirectaService = new VentaDirectaService(
                pedidoRepository, productoRepository, usuarioRepository, autonumeradorService, pagoService);

        Rol rol = new Rol();
        rol.setId(2L);
        rol.setNombre("Cajero");
        cajero = new Usuario();
        cajero.setId(5L);
        cajero.setEmail("cajero@techstore.py");
        cajero.setNombre("Caja");
        cajero.setApellido("Uno");
        cajero.setRol(rol);

        cable = new Producto();
        cable.setId(32L);
        cable.setNombre("Cable HDMI 2.0 2m");
        cable.setPrecio(Dinero.de(45_000, Moneda.PYG));
        cable.setIva(BigDecimal.TEN);
        cable.setActivo(true);
        cable.setStockReal(10);
        cable.setStockComprometido(2);
    }

    private void prepararVentaFeliz() {
        when(usuarioRepository.findByEmail("cajero@techstore.py")).thenReturn(Optional.of(cajero));
        when(productoRepository.findAllForUpdate(anyCollection())).thenReturn(List.of(cable));
        when(autonumeradorService.siguiente("PEDIDO")).thenReturn(50);
        when(pedidoRepository.saveAndFlush(any(Pedido.class))).thenAnswer(inv -> {
            Pedido p = inv.getArgument(0);
            p.setId(99L);
            return p;
        });
    }

    private VentaDirectaRequest venta(int cantidad, String monto) {
        return new VentaDirectaRequest(
                List.of(new ItemVenta(32L, cantidad)),
                List.of(new LineaPago(TipoPago.Efectivo, new BigDecimal(monto), null)),
                null, null);
    }

    @Test
    void laVentaNaceConfirmadaYDescuentaElStockEnElMomento() {
        prepararVentaFeliz();

        ventaDirectaService.vender(venta(3, "135000.00"), "cajero@techstore.py");

        ArgumentCaptor<Pedido> captor = ArgumentCaptor.forClass(Pedido.class);
        verify(pedidoRepository).saveAndFlush(captor.capture());
        Pedido pedido = captor.getValue();

        assertEquals(EstadoPedido.C, pedido.getEstado());
        assertEquals(TipoPedido.Venta, pedido.getTipo());
        assertEquals(cajero, pedido.getUsuario());
        assertEquals(Dinero.de(135_000, Moneda.PYG), pedido.getTotal());
        // La mercadería sale ya: baja el stock real y no toca lo comprometido.
        assertEquals(7, cable.getStockReal());
        assertEquals(2, cable.getStockComprometido());
        assertTrue(pedido.getNotas().contains("Venta directa en mostrador"));
    }

    @Test
    void elCobroYLaFacturaLosHaceElMismoCaminoQueUnaVentaWeb() {
        prepararVentaFeliz();
        RegistroPagoRequest.Receptor receptor =
                new RegistroPagoRequest.Receptor("80012345-0", "Ferretería Del Este SA", null, false);

        ventaDirectaService.vender(
                new VentaDirectaRequest(
                        List.of(new ItemVenta(32L, 1)),
                        List.of(new LineaPago(TipoPago.Efectivo, new BigDecimal("45000.00"), null)),
                        receptor, "Cliente de mostrador"),
                "cajero@techstore.py");

        ArgumentCaptor<RegistroPagoRequest> captor = ArgumentCaptor.forClass(RegistroPagoRequest.class);
        verify(pagoService).registrarPago(eq(99L), captor.capture(), eq("cajero@techstore.py"));
        // El receptor con RUC viaja tal cual al cobro, que es quien emite la factura.
        assertEquals(receptor, captor.getValue().receptor());
        assertEquals(1, captor.getValue().lineas().size());
    }

    @Test
    void noSePuedeVenderMasDeLoDisponible() {
        when(usuarioRepository.findByEmail("cajero@techstore.py")).thenReturn(Optional.of(cajero));
        when(productoRepository.findAllForUpdate(anyCollection())).thenReturn(List.of(cable));
        when(autonumeradorService.siguiente("PEDIDO")).thenReturn(50);

        // Hay 10 reales pero 2 comprometidos por pedidos web: el disponible es 8.
        BusinessException ex = assertThrows(BusinessException.class,
                () -> ventaDirectaService.vender(venta(9, "405000.00"), "cajero@techstore.py"));

        assertTrue(ex.getMessage().contains("disponibles 8"));
        assertEquals(10, cable.getStockReal());
        verify(pagoService, never()).registrarPago(any(), any(), any());
    }

    @Test
    void noSePuedeVenderUnProductoDadoDeBaja() {
        cable.setActivo(false);
        when(usuarioRepository.findByEmail("cajero@techstore.py")).thenReturn(Optional.of(cajero));
        when(productoRepository.findAllForUpdate(anyCollection())).thenReturn(List.of(cable));
        when(autonumeradorService.siguiente("PEDIDO")).thenReturn(50);

        assertThrows(BusinessException.class,
                () -> ventaDirectaService.vender(venta(1, "45000.00"), "cajero@techstore.py"));
        verify(pagoService, never()).registrarPago(any(), any(), any());
    }

    @Test
    void elMismoProductoRepetidoSeFusionaEnUnaSolaLinea() {
        prepararVentaFeliz();

        ventaDirectaService.vender(
                new VentaDirectaRequest(
                        List.of(new ItemVenta(32L, 2), new ItemVenta(32L, 3)),
                        List.of(new LineaPago(TipoPago.Efectivo, new BigDecimal("225000.00"), null)),
                        null, null),
                "cajero@techstore.py");

        ArgumentCaptor<Pedido> captor = ArgumentCaptor.forClass(Pedido.class);
        verify(pedidoRepository).saveAndFlush(captor.capture());
        assertEquals(1, captor.getValue().getDetalles().size());
        assertEquals(5, captor.getValue().getDetalles().get(0).getCantidad());
        assertEquals(5, cable.getStockReal());
    }
}
