package com.techstore.compras;

import com.techstore.catalogo.Producto;
import com.techstore.comun.BusinessException;
import com.techstore.comun.CondicionOperacion;
import com.techstore.comun.Dinero;
import com.techstore.comun.Moneda;
import com.techstore.comun.TipoComprobante;
import com.techstore.ventas.EstadoPedido;
import com.techstore.ventas.Pedido;
import com.techstore.ventas.PedidoDetalle;
import com.techstore.ventas.PedidoRepository;
import com.techstore.ventas.TipoPedido;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Comprobante del proveedor")
class ComprobanteCompraServiceTest {

    private static final Moneda PYG = Moneda.PYG;

    @Mock
    private CompraComprobanteRepository comprobanteRepository;

    @Mock
    private PedidoRepository pedidoRepository;

    private ComprobanteCompraService servicio;
    private Pedido compra;

    @BeforeEach
    void configurar() {
        servicio = new ComprobanteCompraService(comprobanteRepository, pedidoRepository);

        Proveedor proveedor = new Proveedor();
        proveedor.setId(3L);
        proveedor.setRazonSocial("IMPORTADORA S.A.");
        proveedor.setRuc("80011111-7");

        Producto teclado = new Producto();
        teclado.setId(9L);
        teclado.setNombre("Teclado mecánico");

        compra = new Pedido();
        compra.setId(1L);
        compra.setNumero(42);
        compra.setTipo(TipoPedido.Compra);
        compra.setEstado(EstadoPedido.C);
        compra.setMoneda(PYG);
        compra.setProveedor(proveedor);

        PedidoDetalle detalle = new PedidoDetalle();
        detalle.setProducto(teclado);
        detalle.setCantidad(2);
        detalle.setIvaAplicado(BigDecimal.TEN);
        compra.agregarDetalle(detalle);
        detalle.fijarPrecio(Dinero.de(550_000, PYG), null);
        compra.setTotal(Dinero.de(1_100_000, PYG));

        when(pedidoRepository.findWithDetallesById(1L)).thenReturn(Optional.of(compra));
        when(comprobanteRepository.findByPedidoId(any())).thenReturn(Optional.empty());
        when(comprobanteRepository.findByTimbradoAndNumero(anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(comprobanteRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private static ComprobanteCompraRequest factura(String timbrado, String numero) {
        return new ComprobanteCompraRequest(
                109, timbrado, numero, LocalDate.of(2026, 3, 4), CondicionOperacion.Contado,
                null, null, null, null, null, null);
    }

    @Test
    @DisplayName("sin importes cargados toma el desglose de la compra")
    void sinImportesUsaLosDeLaCompra() {
        ComprobanteCompraResponse r = servicio.registrar(1L, factura("81225648", "001-001-0000777"));

        assertEquals(0, new BigDecimal("1100000").compareTo(r.gravado10()));
        assertEquals(0, BigDecimal.ZERO.compareTo(r.gravado5()));
        assertEquals(0, BigDecimal.ZERO.compareTo(r.exento()));
        assertEquals(0, new BigDecimal("1100000").compareTo(r.total()));
    }

    @Test
    @DisplayName("el IVA se extrae del monto gravado, no se guarda")
    void elCreditoFiscalSaleDelGravado() {
        // 1.100.000 al 10% contiene 100.000 de IVA: el impuesto está adentro.
        ComprobanteCompraResponse r = servicio.registrar(1L, factura("81225648", "001-001-0000777"));

        assertEquals(0, new BigDecimal("100000").compareTo(r.iva10()));
        assertEquals(0, new BigDecimal("100000").compareTo(r.creditoFiscal()));
    }

    @Test
    @DisplayName("una nota de crédito del proveedor devuelve crédito fiscal, así que resta")
    void laNotaDeCreditoResta() {
        ComprobanteCompraRequest nota = new ComprobanteCompraRequest(
                110, "81225648", "001-001-0000900", LocalDate.of(2026, 3, 20), CondicionOperacion.Contado,
                new BigDecimal("550000"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("550000"),
                "001-001-0000777", "81225648");

        ComprobanteCompraResponse r = servicio.registrar(1L, nota);

        assertEquals(0, new BigDecimal("-50000").compareTo(r.creditoFiscal()));
    }

    @Test
    @DisplayName("el número se completa con ceros como viene tipeado del papel")
    void normalizaElNumero() {
        ComprobanteCompraResponse r = servicio.registrar(1L, factura("81225648", "1-1-777"));

        assertEquals("001-001-0000777", r.numero());
    }

    @Test
    @DisplayName("una factura sin timbrado no se puede asentar")
    void laFacturaExigeTimbrado() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> servicio.registrar(1L, factura(null, "001-001-0000777")));

        assertEquals("compras.comprobante.timbrado.obligatorio", error.getClave());
    }

    @Test
    @DisplayName("una factura sin número tampoco")
    void laFacturaExigeNumero() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> servicio.registrar(1L, factura("81225648", null)));

        assertEquals("compras.comprobante.numero.obligatorio", error.getClave());
    }

    @Test
    @DisplayName("una nota de crédito tiene que decir qué comprobante corrige")
    void laNotaDeCreditoExigeElAsociado() {
        ComprobanteCompraRequest sinAsociado = new ComprobanteCompraRequest(
                110, "81225648", "001-001-0000900", LocalDate.of(2026, 3, 20), CondicionOperacion.Contado,
                null, null, null, null, null, null);

        BusinessException error = assertThrows(BusinessException.class,
                () -> servicio.registrar(1L, sinAsociado));

        assertEquals("compras.comprobante.asociado.obligatorio", error.getClave());
    }

    @Test
    @DisplayName("el mismo comprobante no se puede cargar dos veces")
    void noSePuedeDuplicarElCreditoFiscal() {
        Pedido otraCompra = new Pedido();
        otraCompra.setNumero(11);
        CompraComprobante yaCargado = new CompraComprobante();
        yaCargado.setId(99L);
        yaCargado.setPedido(otraCompra);
        when(comprobanteRepository.findByTimbradoAndNumero("81225648", "001-001-0000777"))
                .thenReturn(Optional.of(yaCargado));

        BusinessException error = assertThrows(BusinessException.class,
                () -> servicio.registrar(1L, factura("81225648", "001-001-0000777")));

        assertEquals("compras.comprobante.duplicado", error.getClave());
    }

    @Test
    @DisplayName("corregir el propio comprobante no cuenta como duplicado")
    void corregirElPropioNoEsDuplicado() {
        CompraComprobante propio = new CompraComprobante();
        propio.setId(50L);
        propio.setPedido(compra);
        when(comprobanteRepository.findByPedidoId(1L)).thenReturn(Optional.of(propio));
        when(comprobanteRepository.findByTimbradoAndNumero("81225648", "001-001-0000777"))
                .thenReturn(Optional.of(propio));

        ComprobanteCompraResponse r = servicio.registrar(1L, factura("81225648", "001-001-0000777"));

        assertEquals("001-001-0000777", r.numero());
    }

    @Test
    @DisplayName("una fecha futura es un error de tipeo")
    void rechazaLaFechaFutura() {
        ComprobanteCompraRequest futura = new ComprobanteCompraRequest(
                109, "81225648", "001-001-0000777", LocalDate.now().plusDays(1), null,
                null, null, null, null, null, null);

        BusinessException error = assertThrows(BusinessException.class,
                () -> servicio.registrar(1L, futura));

        assertEquals("compras.comprobante.fecha.futura", error.getClave());
    }

    @Test
    @DisplayName("el desglose tiene que sumar el total del comprobante")
    void elDesgloseTieneQueCuadrar() {
        ComprobanteCompraRequest descuadrado = new ComprobanteCompraRequest(
                109, "81225648", "001-001-0000777", LocalDate.of(2026, 3, 4), CondicionOperacion.Contado,
                new BigDecimal("1000000"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("1100000"),
                null, null);

        BusinessException error = assertThrows(BusinessException.class,
                () -> servicio.registrar(1L, descuadrado));

        assertEquals("compras.comprobante.desglose.no.cuadra", error.getClave());
    }

    @Test
    @DisplayName("un ticket se declara solo con el total, sin abrir por tasa")
    void elTicketVaSinDesglose() {
        ComprobanteCompraRequest ticket = new ComprobanteCompraRequest(
                112, "81225648", null, LocalDate.of(2026, 3, 4), CondicionOperacion.Contado,
                null, null, null, new BigDecimal("45000"), null, null);

        ComprobanteCompraResponse r = servicio.registrar(1L, ticket);

        assertNull(r.numero(), "el ticket no lleva número de comprobante");
        assertEquals(0, BigDecimal.ZERO.compareTo(r.gravado10()));
        assertEquals(0, new BigDecimal("45000").compareTo(r.total()));
        assertEquals(0, BigDecimal.ZERO.compareTo(r.creditoFiscal()), "un ticket no da crédito fiscal");
    }

    @Test
    @DisplayName("el despacho de importación va sin timbrado")
    void elDespachoNoLlevaTimbrado() {
        ComprobanteCompraRequest despacho = new ComprobanteCompraRequest(
                107, null, null, LocalDate.of(2026, 3, 4), CondicionOperacion.Contado,
                null, null, null, null, null, null);

        ComprobanteCompraResponse r = servicio.registrar(1L, despacho);

        assertNull(r.timbrado());
        assertEquals(TipoComprobante.DespachoImportacion.codigo(), r.tipoComprobante());
    }

    @Test
    @DisplayName("una compra anulada no lleva comprobante")
    void laCompraAnuladaNoAdmiteComprobante() {
        compra.setEstado(EstadoPedido.X);

        BusinessException error = assertThrows(BusinessException.class,
                () -> servicio.registrar(1L, factura("81225648", "001-001-0000777")));

        assertEquals("compras.comprobante.compra.anulada", error.getClave());
    }

    @Test
    @DisplayName("sin el RUC del proveedor no hay libro de compras posible")
    void exigeElRucDelProveedor() {
        compra.getProveedor().setRuc(null);

        BusinessException error = assertThrows(BusinessException.class,
                () -> servicio.registrar(1L, factura("81225648", "001-001-0000777")));

        assertEquals("compras.comprobante.proveedor.sin.ruc", error.getClave());
    }

    @Test
    @DisplayName("el proveedor queda congelado en el comprobante")
    void congelaAlProveedor() {
        ComprobanteCompraResponse r = servicio.registrar(1L, factura("81225648", "001-001-0000777"));

        assertEquals("80011111-7", r.proveedorRuc());
        assertEquals("IMPORTADORA S.A.", r.proveedorRazonSocial());
    }

    @Test
    @DisplayName("un pedido de venta no es una compra")
    void rechazaUnPedidoDeVenta() {
        compra.setTipo(TipoPedido.Venta);

        BusinessException error = assertThrows(BusinessException.class,
                () -> servicio.registrar(1L, factura("81225648", "001-001-0000777")));

        assertEquals("compras.pedido.no.es.compra", error.getClave());
    }
}
