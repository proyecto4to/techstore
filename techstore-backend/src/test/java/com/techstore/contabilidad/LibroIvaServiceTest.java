package com.techstore.contabilidad;

import com.techstore.catalogo.Producto;
import com.techstore.compras.CompraComprobanteRepository;
import com.techstore.comun.CondicionOperacion;
import com.techstore.comun.Dinero;
import com.techstore.comun.Moneda;
import com.techstore.comun.TipoIdentificacion;
import com.techstore.empresa.EmpresaConfigService;
import com.techstore.empresa.EmpresaProperties;
import com.techstore.facturacion.EstadoFactura;
import com.techstore.facturacion.Factura;
import com.techstore.facturacion.FacturaRepository;
import com.techstore.facturacion.NotaCredito;
import com.techstore.facturacion.NotaCreditoDetalle;
import com.techstore.facturacion.NotaCreditoRepository;
import com.techstore.ventas.Pedido;
import com.techstore.ventas.PedidoDetalle;
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
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Libro de IVA de ventas")
class LibroIvaServiceTest {

    private static final Moneda PYG = Moneda.PYG;
    private static final LocalDate DESDE = LocalDate.of(2026, 3, 1);
    private static final LocalDate HASTA = LocalDate.of(2026, 3, 31);

    @Mock
    private FacturaRepository facturaRepository;

    @Mock
    private NotaCreditoRepository notaCreditoRepository;

    @Mock
    private CompraComprobanteRepository comprobanteRepository;

    @Mock
    private EmpresaConfigService empresaConfig;

    private LibroIvaService servicio;

    @BeforeEach
    void configurar() {
        servicio = new LibroIvaService(facturaRepository, notaCreditoRepository,
                comprobanteRepository, empresaConfig);
        when(empresaConfig.actual()).thenReturn(emisor());
        when(facturaRepository.delPeriodo(any(), any())).thenReturn(List.of());
        when(notaCreditoRepository.delPeriodo(any(), any())).thenReturn(List.of());
        when(comprobanteRepository.delPeriodo(any(), any())).thenReturn(List.of());
    }

    private static EmpresaProperties emisor() {
        return new EmpresaProperties(
                "TechStore Paraguay", "80024627-6", null, null, null, null, null, "11138251", null,
                2, "001", "001", null, null, null, null, null, null, null, "2026-01-01", "0");
    }

    /** Una factura de una sola línea al 10%, con el IVA ya incluido en el precio. */
    private static Factura factura(String numero, long total, String ruc) {
        Producto producto = new Producto();
        producto.setId(1L);
        producto.setNombre("Producto");

        Pedido pedido = new Pedido();
        pedido.setMoneda(PYG);
        PedidoDetalle detalle = new PedidoDetalle();
        detalle.setProducto(producto);
        detalle.setCantidad(1);
        detalle.setIvaAplicado(BigDecimal.TEN);
        pedido.agregarDetalle(detalle);
        detalle.fijarPrecio(Dinero.de(total, PYG), null);
        pedido.setTotal(Dinero.de(total, PYG));

        Factura f = new Factura();
        f.setNumero(numero);
        f.setTimbrado("11138251");
        f.setCondicionVenta(CondicionOperacion.Contado);
        f.setEstado(EstadoFactura.Pagada);
        f.setFecha(LocalDateTime.of(2026, 3, 10, 9, 0));
        f.setMoneda(PYG);
        f.setPedido(pedido);
        f.setSubtotal(Dinero.de(total, PYG).sinIva(BigDecimal.TEN));
        f.setIva(Dinero.de(total, PYG).ivaIncluido(BigDecimal.TEN));
        f.setTotal(Dinero.de(total, PYG));
        if (ruc != null) {
            f.setReceptorRuc(ruc);
            f.setReceptorNombre("CLIENTE S.A.");
            f.setReceptorTipoDocumento(TipoIdentificacion.RUC);
        }
        return f;
    }

    private static NotaCredito notaCredito(String numero, Factura factura, long total, boolean conDetalle) {
        NotaCredito nc = new NotaCredito();
        nc.setNumero(numero);
        nc.setFactura(factura);
        nc.setMoneda(PYG);
        nc.setFecha(LocalDateTime.of(2026, 3, 20, 15, 0));
        nc.setMotivo("Devolución");
        nc.setSubtotal(Dinero.de(total, PYG).sinIva(BigDecimal.TEN));
        nc.setIva(Dinero.de(total, PYG).ivaIncluido(BigDecimal.TEN));
        nc.setTotal(Dinero.de(total, PYG));
        if (conDetalle) {
            Producto producto = new Producto();
            producto.setId(1L);
            producto.setNombre("Producto");
            NotaCreditoDetalle d = new NotaCreditoDetalle();
            d.setProducto(producto);
            d.setCantidad(1);
            d.setIvaAplicado(BigDecimal.TEN);
            nc.agregarDetalle(d);
            d.fijarPrecio(Dinero.de(total, PYG), null);
        }
        return nc;
    }

    @Test
    @DisplayName("una factura entra con su desglose y su IVA extraído del precio")
    void laFacturaEntraConSuIva() {
        when(facturaRepository.delPeriodo(any(), any()))
                .thenReturn(List.of(factura("001-001-0000010", 1_100_000, "80024627-6")));

        LibroIvaResponse libro = servicio.ventas(DESDE, HASTA);

        assertEquals(1, libro.totales().comprobantes());
        assertEquals(0, new BigDecimal("1100000").compareTo(libro.totales().gravado10()));
        assertEquals(0, new BigDecimal("100000").compareTo(libro.totales().iva10()));
        assertEquals(List.of(), libro.avisos());
    }

    @Test
    @DisplayName("la nota de crédito resta débito fiscal en vez de sumarlo")
    void laNotaDeCreditoResta() {
        Factura f = factura("001-001-0000010", 1_100_000, "80024627-6");
        when(facturaRepository.delPeriodo(any(), any())).thenReturn(List.of(f));
        when(notaCreditoRepository.delPeriodo(any(), any()))
                .thenReturn(List.of(notaCredito("001-001-0000003", f, 550_000, true)));

        LibroIvaResponse libro = servicio.ventas(DESDE, HASTA);

        assertEquals(2, libro.totales().comprobantes());
        // 1.100.000 vendidos menos 550.000 devueltos.
        assertEquals(0, new BigDecimal("550000").compareTo(libro.totales().total()));
        assertEquals(0, new BigDecimal("50000").compareTo(libro.totales().iva()));
        assertEquals(List.of(), libro.avisos());
    }

    @Test
    @DisplayName("una nota de crédito sin detalle no se puede abrir por tasa, y se avisa")
    void laNotaSinDetalleSeAvisa() {
        // Pasa de verdad: las notas de crédito anteriores a que el sistema
        // guardara el detalle línea por línea tienen total pero no tienen ítems.
        // Sin este aviso el archivo saldría con el total sin respaldo en las
        // columnas, y la DNIT lo rechaza entero.
        Factura f = factura("001-001-0000010", 1_100_000, "80024627-6");
        when(facturaRepository.delPeriodo(any(), any())).thenReturn(List.of(f));
        when(notaCreditoRepository.delPeriodo(any(), any()))
                .thenReturn(List.of(notaCredito("001-001-0000001", f, 800_000, false)));

        LibroIvaResponse libro = servicio.ventas(DESDE, HASTA);

        assertEquals(1, libro.avisos().size());
        assertTrue(libro.avisos().get(0).contains("001-001-0000001"), libro.avisos().get(0));
        assertTrue(libro.avisos().get(0).contains("suma de las columnas"), libro.avisos().get(0));
    }

    @Test
    @DisplayName("la venta a consumidor final se declara como 'sin nombre'")
    void elConsumidorFinalVaSinNombre() {
        when(facturaRepository.delPeriodo(any(), any()))
                .thenReturn(List.of(factura("001-001-0000011", 550_000, null)));

        LibroIvaResponse libro = servicio.ventas(DESDE, HASTA);

        LibroIvaResponse.Linea linea = libro.lineas().get(0);
        assertEquals(TipoIdentificacion.SinNombre.codigo(), linea.tipoIdentificacion());
        assertEquals("0", linea.identificacion(), "sale el número configurado en Admin → Empresa");
        assertEquals(List.of(), libro.avisos());
    }

    @Test
    @DisplayName("una factura sin timbrado registrado no se puede declarar")
    void avisaSiFaltaElTimbrado() {
        Factura sinTimbrado = factura("001-001-0000012", 550_000, "80024627-6");
        sinTimbrado.setTimbrado(null);
        when(facturaRepository.delPeriodo(any(), any())).thenReturn(List.of(sinTimbrado));

        LibroIvaResponse libro = servicio.ventas(DESDE, HASTA);

        assertEquals(1, libro.avisos().size());
        assertTrue(libro.avisos().get(0).contains("timbrado"), libro.avisos().get(0));
    }

    @Test
    @DisplayName("el libro sale en orden cronológico aunque las notas lleguen después")
    void ordenCronologico() {
        Factura f = factura("001-001-0000010", 1_100_000, "80024627-6");
        Factura otra = factura("001-001-0000011", 200_000, "80024627-6");
        otra.setFecha(LocalDateTime.of(2026, 3, 25, 11, 0));
        when(facturaRepository.delPeriodo(any(), any())).thenReturn(List.of(f, otra));
        when(notaCreditoRepository.delPeriodo(any(), any()))
                .thenReturn(List.of(notaCredito("001-001-0000003", f, 100_000, true)));

        LibroIvaResponse libro = servicio.ventas(DESDE, HASTA);

        assertEquals(List.of(LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 20), LocalDate.of(2026, 3, 25)),
                libro.lineas().stream().map(LibroIvaResponse.Linea::fecha).toList());
    }

    @Test
    @DisplayName("la liquidación es el débito de las ventas menos el crédito de las compras")
    void laLiquidacionRestaLosDosLibros() {
        when(facturaRepository.delPeriodo(any(), any()))
                .thenReturn(List.of(factura("001-001-0000010", 1_100_000, "80024627-6")));

        ResumenIvaResponse resumen = servicio.resumen(DESDE, HASTA);

        assertEquals(0, new BigDecimal("100000").compareTo(resumen.debitoFiscal()));
        assertEquals(0, BigDecimal.ZERO.compareTo(resumen.creditoFiscal()));
        assertEquals(0, new BigDecimal("100000").compareTo(resumen.saldo()));
        assertEquals("A pagar", resumen.situacion());
    }
}
