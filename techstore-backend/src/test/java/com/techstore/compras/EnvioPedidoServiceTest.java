package com.techstore.compras;

import com.techstore.catalogo.Producto;
import com.techstore.comun.BusinessException;
import com.techstore.comun.Dinero;
import com.techstore.comun.Moneda;
import com.techstore.empresa.EmpresaConfigService;
import com.techstore.empresa.EmpresaProperties;
import com.techstore.notificaciones.CorreoService;
import com.techstore.notificaciones.EmailTemplateService;
import com.techstore.notificaciones.EstadoCorreo;
import com.techstore.notificaciones.TipoCorreo;
import com.techstore.ventas.EstadoPedido;
import com.techstore.ventas.Pedido;
import com.techstore.ventas.PedidoDetalle;
import com.techstore.ventas.PedidoRepository;
import com.techstore.ventas.TipoPedido;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.util.HtmlUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static com.techstore.empresa.EmisorDePrueba.config;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Envío del pedido al proveedor")
class EnvioPedidoServiceTest {

    private static final Moneda PYG = Moneda.PYG;

    @Mock
    private PedidoRepository pedidoRepository;

    @Mock
    private CorreoService correoService;

    @Mock
    private EmpresaConfigService empresaConfig;

    private EnvioPedidoService servicio;
    private Pedido compra;
    private Proveedor proveedor;

    @BeforeEach
    void configurar() {
        // La plantilla real, no un mock: parte de lo que se comprueba es que el
        // pedido llegue con sus ítems adentro del HTML.
        EmailTemplateService plantilla = new EmailTemplateService(config(emisor()), "http://localhost:5173");
        servicio = new EnvioPedidoService(pedidoRepository, correoService, plantilla, empresaConfig);

        when(empresaConfig.actual()).thenReturn(emisor());
        when(correoService.enviar(any(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(EstadoCorreo.ENVIADO);
        when(pedidoRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        proveedor = new Proveedor();
        proveedor.setId(3L);
        proveedor.setRazonSocial("Distribuidora Tech SA");
        proveedor.setEmail("ventas@distribuidora.com.py");

        Producto teclado = new Producto();
        teclado.setId(9L);
        teclado.setNombre("Teclado mecánico RGB");

        compra = new Pedido();
        compra.setId(1L);
        compra.setNumero(47);
        compra.setTipo(TipoPedido.Compra);
        compra.setEstado(EstadoPedido.P);
        compra.setMoneda(PYG);
        compra.setProveedor(proveedor);

        PedidoDetalle detalle = new PedidoDetalle();
        detalle.setProducto(teclado);
        detalle.setCantidad(3);
        detalle.setIvaAplicado(BigDecimal.TEN);
        compra.agregarDetalle(detalle);
        detalle.fijarPrecio(Dinero.de(250_000, PYG), null);
        compra.setTotal(Dinero.de(750_000, PYG));

        when(pedidoRepository.findWithDetallesById(1L)).thenReturn(Optional.of(compra));
    }

    private static EmpresaProperties emisor() {
        return new EmpresaProperties(
                "TechStore Paraguay", "80012345-6", "Avda. España 1234", "Asunción",
                "(021) 555-123", "compras@techstore.py", "Venta de equipos", "12345678", null,
                2, "001", "001", null, null, null, null, null, null, null, "2026-01-01", "0");
    }

    private String cuerpoEnviado() {
        ArgumentCaptor<String> cuerpo = ArgumentCaptor.forClass(String.class);
        verify(correoService).enviar(eq(TipoCorreo.PEDIDO_PROVEEDOR), anyString(), anyString(),
                anyString(), cuerpo.capture(), anyString());
        return cuerpo.getValue();
    }

    @Test
    @DisplayName("manda el pedido y lo deja registrado")
    void mandaYRegistra() {
        EnvioPedidoResponse r = servicio.enviar(1L, CanalEnvio.Correo);

        assertTrue(r.enviado());
        assertEquals("ventas@distribuidora.com.py", r.destinatario());
        assertEquals(47, r.numero());
        assertNotNull(compra.getEnviadoEn(), "no quedó registrado cuándo salió");
        assertEquals(CanalEnvio.Correo, compra.getEnviadoCanal());
    }

    @Test
    @DisplayName("el correo lleva los productos, las cantidades y el total")
    void elCorreoLlevaElDetalle() {
        servicio.enviar(1L, CanalEnvio.Correo);

        String cuerpo = cuerpoEnviado();
        // Todo lo que viene de la base va escapado —un nombre de producto es
        // texto que alguien cargó—, así que los acentos viajan como entidades.
        assertTrue(cuerpo.contains(HtmlUtils.htmlEscape("Teclado mecánico RGB")), "falta el producto");
        assertTrue(cuerpo.contains("750.000"), "falta el total");
        assertTrue(cuerpo.contains(HtmlUtils.htmlEscape("Pedido de compra N.º 47")),
                "falta el número de pedido");
        assertTrue(cuerpo.contains("80012345-6"), "el proveedor tiene que saber quién le pide");
        assertTrue(cuerpo.contains("<td"), "el detalle tiene que ir en una tabla, no en una línea suelta");
    }

    @Test
    @DisplayName("un nombre de producto con HTML adentro no rompe el correo")
    void elNombreConHtmlSeEscapa() {
        compra.getDetalles().get(0).getProducto().setNombre("Teclado <script>alert(1)</script>");

        servicio.enviar(1L, CanalEnvio.Correo);

        String cuerpo = cuerpoEnviado();
        assertFalse(cuerpo.contains("<script>"), "el nombre se tiene que escapar");
        assertTrue(cuerpo.contains("&lt;script&gt;"));
    }

    @Test
    @DisplayName("al proveedor se le pide que responda, no se le dice que no responda")
    void elPieInvitaAResponder() {
        servicio.enviar(1L, CanalEnvio.Correo);

        String cuerpo = cuerpoEnviado();
        assertFalse(cuerpo.contains("no respondas a este correo"),
                "el pie genérico contradice el sentido del correo");
        assertTrue(cuerpo.contains("compras@techstore.py"), "tiene que decir a dónde llega la respuesta");
    }

    @Test
    @DisplayName("si el correo no sale de verdad, la compra no queda marcada como enviada")
    void elCorreoSimuladoNoMarcaLaCompra() {
        // Sin SMTP configurado el correo se registra pero no viaja. Marcarla como
        // enviada dejaría a alguien esperando una mercadería que nadie pidió.
        when(correoService.enviar(any(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(EstadoCorreo.SIMULADO);

        EnvioPedidoResponse r = servicio.enviar(1L, CanalEnvio.Correo);

        assertFalse(r.enviado());
        assertTrue(r.detalle().contains("NO se envió"), r.detalle());
        assertNull(compra.getEnviadoEn());
        verify(pedidoRepository, never()).save(any());
    }

    @Test
    @DisplayName("si el servidor lo rechaza, tampoco")
    void elErrorDeSmtpNoMarcaLaCompra() {
        when(correoService.enviar(any(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(EstadoCorreo.ERROR);

        EnvioPedidoResponse r = servicio.enviar(1L, CanalEnvio.Correo);

        assertFalse(r.enviado());
        assertNull(compra.getEnviadoEn());
    }

    @Test
    @DisplayName("WhatsApp avisa antes de mandar nada")
    void whatsappTodaviaNo() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> servicio.enviar(1L, CanalEnvio.WhatsApp));

        assertEquals("compras.envio.whatsapp.no.disponible", error.getClave());
        verify(correoService, never()).enviar(any(), anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("'ambos' no manda el correo a medias")
    void ambosNoMandaSoloElCorreo() {
        // Si mandara el correo y fallara en WhatsApp, quedaría medio hecho sin
        // que nadie se entere de cuál mitad salió.
        assertThrows(BusinessException.class, () -> servicio.enviar(1L, CanalEnvio.Ambos));

        verify(correoService, never()).enviar(any(), anyString(), anyString(), anyString(), anyString(), anyString());
        assertNull(compra.getEnviadoEn());
    }

    @Test
    @DisplayName("un proveedor sin correo cargado no se puede contactar")
    void proveedorSinCorreo() {
        proveedor.setEmail("   ");

        BusinessException error = assertThrows(BusinessException.class,
                () -> servicio.enviar(1L, CanalEnvio.Correo));

        assertEquals("compras.envio.proveedor.sin.correo", error.getClave());
    }

    @Test
    @DisplayName("una compra ya recibida no se manda")
    void soloSeMandaLoPendiente() {
        compra.setEstado(EstadoPedido.C);

        BusinessException error = assertThrows(BusinessException.class,
                () -> servicio.enviar(1L, CanalEnvio.Correo));

        assertEquals("compras.envio.solo.pendiente", error.getClave());
    }

    @Test
    @DisplayName("una compra anulada tampoco")
    void laAnuladaNoSeManda() {
        compra.setEstado(EstadoPedido.X);

        assertThrows(BusinessException.class, () -> servicio.enviar(1L, CanalEnvio.Correo));
    }

    @Test
    @DisplayName("mandar el pedido no lo confirma: la compra sigue Pendiente")
    void mandarNoConfirma() {
        servicio.enviar(1L, CanalEnvio.Correo);

        assertEquals(EstadoPedido.P, compra.getEstado());
    }

    @Test
    @DisplayName("un pedido de venta no se le manda a ningún proveedor")
    void rechazaUnPedidoDeVenta() {
        compra.setTipo(TipoPedido.Venta);

        BusinessException error = assertThrows(BusinessException.class,
                () -> servicio.enviar(1L, CanalEnvio.Correo));

        assertEquals("compras.pedido.no.es.compra", error.getClave());
    }
}
