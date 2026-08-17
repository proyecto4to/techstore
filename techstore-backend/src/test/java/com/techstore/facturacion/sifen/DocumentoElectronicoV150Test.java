package com.techstore.facturacion.sifen;

import com.techstore.catalogo.Producto;
import com.techstore.comun.CondicionOperacion;
import com.techstore.comun.Dinero;
import com.techstore.comun.Moneda;
import com.techstore.comun.TipoIdentificacion;
import com.techstore.empresa.EmisorDePrueba;
import com.techstore.empresa.EmpresaProperties;
import com.techstore.facturacion.EstadoFactura;
import com.techstore.facturacion.Factura;
import com.techstore.facturacion.FacturaRepository;
import com.techstore.usuarios.Usuario;
import com.techstore.ventas.EstadoPedido;
import com.techstore.ventas.Pedido;
import com.techstore.ventas.PedidoDetalle;
import com.techstore.ventas.TipoPedido;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.dom.DOMValidateContext;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.SchemaFactory;
import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.math.BigDecimal;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DocumentoElectronicoV150Test {

    private static final String NS = "http://ekuatia.set.gov.py/sifen/xsd";
    private static final String DS = "http://www.w3.org/2000/09/xmldsig#";
    // El mismo CSC con el que se configuró el generador: si divergieran, el
    // hash del QR fallaría sin decir por qué.
    private static final String CSC_PRUEBA = SifenDePrueba.CSC;

    private Factura factura;
    private KudeService kudeService;
    private FacturaElectronicaService facturaElectronicaService;

    @BeforeEach
    void setUp() {
        // Referencia geográfica oficial de Asunción: departamento 1 (CAPITAL),
        // distrito 1 y ciudad 1. Evita el cruce incoherente CENTRAL/ASUNCION.
        EmpresaProperties emisor = new EmpresaProperties(
                "TechStore Paraguay", "44444401-7", "Avda. España 1234", "Asunción, Paraguay",
                "021555123", "facturacion@techstore.py", "Venta de equipos e insumos informáticos",
                "12345678", "01/01/2026 al 31/12/2026", 2, "001", "001", "47411",
                "1", "CAPITAL", "1", "ASUNCION", "1", "ASUNCION", "2026-01-01", "0");
        var empresaConfig = EmisorDePrueba.config(emisor);

        factura = facturaDePrueba();
        SifenProperties sifen = SifenDePrueba.test();
        var documentoService = new DocumentoElectronicoService(empresaConfig, sifen);
        var firmaService = new FirmaDigitalService();
        kudeService = new KudeService(empresaConfig, sifen);
        FacturaRepository repository = mock(FacturaRepository.class);
        when(repository.findParaPdfById(41L)).thenReturn(Optional.of(factura));
        facturaElectronicaService = new FacturaElectronicaService(
                repository, documentoService, firmaService, kudeService, sifen);
    }

    @Test
    void xmlCompletoFirmadoValidaContraElXsdOficialV150SinRed() throws Exception {
        String xml = xmlCompleto().xml();
        URL recurso = Objects.requireNonNull(
                getClass().getResource("/sifen/xsd/siRecepDE_v150.xsd"), "Falta siRecepDE_v150.xsd");
        SchemaFactory fabrica = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        fabrica.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        // Los includes son copias locales de los catálogos oficiales; HTTP queda bloqueado.
        fabrica.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "file");
        var validador = fabrica.newSchema(recurso).newValidator();

        assertDoesNotThrow(() -> validador.validate(new StreamSource(new StringReader(xml))));

        Document doc = parsear(xml);
        assertEquals(List.of("dVerFor", "DE", "Signature", "gCamFuFD"),
                hijosElemento(doc.getDocumentElement()));
    }

    @Test
    void usaCamposIvaBasesExentasTotalesYCatalogosEnLaSecuenciaV150() throws Exception {
        Document doc = parsear(xmlCompleto().xml());

        assertEquals("facturacion@techstore.py", texto(doc, "dEmailE"));
        assertEquals("1", texto(doc, "cDepEmi"));
        assertEquals("CAPITAL", texto(doc, "dDesDepEmi"));
        assertEquals("1", texto(doc, "cDisEmi"));
        assertEquals("1", texto(doc, "cCiuEmi"));
        assertEquals(0, doc.getElementsByTagNameNS(NS, "iAfectIVA").getLength());

        var gruposIva = doc.getElementsByTagNameNS(NS, "gCamIVA");
        assertEquals(2, gruposIva.getLength());
        Element gravado = (Element) gruposIva.item(0);
        assertEquals("1", texto(gravado, "iAfecIVA"));
        assertEquals("100000", texto(gravado, "dBasGravIVA"));
        assertEquals("10000", texto(gravado, "dLiqIVAItem"));
        assertEquals("0", texto(gravado, "dBasExe"));

        Element exento = (Element) gruposIva.item(1);
        assertEquals("3", texto(exento, "iAfecIVA"));
        assertEquals("0", texto(exento, "dBasGravIVA"));
        assertEquals("0", texto(exento, "dLiqIVAItem"));
        assertEquals("50000", texto(exento, "dBasExe"));

        Element totales = (Element) doc.getElementsByTagNameNS(NS, "gTotSub").item(0);
        assertEquals(List.of(
                "dSubExe", "dSub5", "dSub10", "dTotOpe", "dTotDesc", "dTotDescGlotem",
                "dTotAntItem", "dTotAnt", "dPorcDescTotal", "dDescTotal", "dAnticipo", "dRedon",
                "dTotGralOpe", "dIVA5", "dIVA10", "dTotIVA", "dBaseGrav5", "dBaseGrav10",
                "dTBasGraIVA"), hijosElemento(totales));
        assertEquals("160000", texto(totales, "dTotGralOpe"));
        assertEquals("10000", texto(totales, "dTotIVA"));
    }

    @Test
    void dCarQrUsaElDigestDeLaFirmaYElKudeConsumeEsaMismaUrl() throws Exception {
        FacturaElectronicaService.XmlFirmado resultado = xmlCompleto();
        String xml = resultado.xml();
        String url = kudeService.extraerDCarQr(xml);
        assertTrue(url.startsWith("https://ekuatia.set.gov.py/consultas-test/qr?nVersion=150&"));
        assertTrue(xml.contains("nVersion=150&amp;Id="), "Los & deben quedar escapados dentro del XML");

        Document doc = parsear(xml);
        Map<String, String> parametros = parametros(url);
        assertEquals(resultado.cdc(), parametros.get("Id"));
        assertEquals(hex("2026-08-04T10:30:00"), parametros.get("dFeEmiDE"));
        assertEquals("0", parametros.get("dRucRec"));
        assertEquals("160000", parametros.get("dTotGralOpe"));
        assertEquals("10000", parametros.get("dTotIVA"));
        assertEquals("2", parametros.get("cItems"));
        assertEquals(hex(textoNs(doc, DS, "DigestValue")), parametros.get("DigestValue"));

        String sinHash = url.substring(url.indexOf('?') + 1, url.lastIndexOf("&cHashQR="));
        assertEquals(sha256Hex(sinHash + CSC_PRUEBA), parametros.get("cHashQR"));

        byte[] pdf = kudeService.generar(factura, resultado.cdc(), xml);
        assertArrayEquals("%PDF-".getBytes(StandardCharsets.US_ASCII),
                java.util.Arrays.copyOf(pdf, 5));
    }

    @Test
    void agregarElQrFueraDeDeNoInvalidaLaFirmaConCertificadoDePrueba() throws Exception {
        Document doc = parsear(xmlCompleto().xml());
        Element de = (Element) doc.getElementsByTagNameNS(NS, "DE").item(0);
        de.setIdAttribute("Id", true);

        String certificadoBase64 = textoNs(doc, DS, "X509Certificate").replaceAll("\\s", "");
        X509Certificate certificado = (X509Certificate) CertificateFactory.getInstance("X.509")
                .generateCertificate(new ByteArrayInputStream(Base64.getDecoder().decode(certificadoBase64)));
        DOMValidateContext contexto = new DOMValidateContext(
                certificado.getPublicKey(), doc.getElementsByTagNameNS(DS, "Signature").item(0));
        var firma = XMLSignatureFactory.getInstance("DOM").unmarshalXMLSignature(contexto);

        assertTrue(firma.validate(contexto));
        assertEquals(certificado.getSubjectX500Principal(), certificado.getIssuerX500Principal(),
                "El certificado incluido debe seguir siendo el autofirmado de prueba");
    }

    @Test
    void consumidorFinalUsaSinNombreYNoLaIdentidadDelCajero() throws Exception {
        assertEquals("Sin nombre", KudeService.nombreReceptor(factura));
        assertFalse(KudeService.nombreReceptor(factura).contains("Caja Uno"));
        assertEquals("Sin nombre", texto(parsear(xmlCompleto().xml()), "dNomRec"));
    }

    @Test
    void servicioDeEntregaCierraItemsTotalesEIvaDelDocumentoElectronico() throws Exception {
        factura.getPedido().setCostoEnvio(Dinero.de(22_000, Moneda.PYG));
        factura.getPedido().setIvaEnvio(BigDecimal.TEN);
        factura.getPedido().setTotal(Dinero.de(182_000, Moneda.PYG));
        factura.setSubtotal(Dinero.de(170_000, Moneda.PYG));
        factura.setIva(Dinero.de(12_000, Moneda.PYG));
        factura.setTotal(Dinero.de(182_000, Moneda.PYG));

        Document doc = parsear(xmlCompleto().xml());

        assertEquals(3, doc.getElementsByTagNameNS(NS, "gCamItem").getLength());
        assertTrue(doc.getElementsByTagNameNS(NS, "gCamItem").item(2).getTextContent()
                .contains("Servicio de entrega"));
        Element totales = (Element) doc.getElementsByTagNameNS(NS, "gTotSub").item(0);
        assertEquals("182000", texto(totales, "dTotGralOpe"));
        assertEquals("12000", texto(totales, "dIVA10"));
        assertEquals("12000", texto(totales, "dTotIVA"));
    }

    private FacturaElectronicaService.XmlFirmado xmlCompleto() {
        return facturaElectronicaService.generarXml(41L);
    }

    private static Factura facturaDePrueba() {
        Usuario cajero = new Usuario();
        cajero.setNombre("Caja");
        cajero.setApellido("Uno");
        cajero.setEmail("cajero@techstore.py");

        Pedido pedido = new Pedido();
        pedido.setNumero(7);
        pedido.setTipo(TipoPedido.Venta);
        pedido.setEstado(EstadoPedido.G);
        pedido.setUsuario(cajero);
        agregarItem(pedido, 1L, "Servicio gravado", 110_000, BigDecimal.TEN);
        agregarItem(pedido, 2L, "Producto exento", 50_000, BigDecimal.ZERO);
        pedido.setTotal(Dinero.de(160_000, Moneda.PYG));

        Factura factura = new Factura();
        factura.setId(41L);
        factura.setNumero("001-001-0000001");
        factura.setCodigoSeguridad("123456789");
        factura.setTimbrado("12345678");
        factura.setCondicionVenta(CondicionOperacion.Contado);
        factura.setPedido(pedido);
        factura.setFecha(LocalDateTime.of(2026, 8, 4, 10, 30));
        factura.setSubtotal(Dinero.de(150_000, Moneda.PYG));
        factura.setIva(Dinero.de(10_000, Moneda.PYG));
        factura.setTotal(Dinero.de(160_000, Moneda.PYG));
        factura.setEstado(EstadoFactura.Pagada);
        factura.setUsuario(cajero);
        factura.setReceptorTipoDocumento(TipoIdentificacion.SinNombre);
        return factura;
    }

    private static void agregarItem(Pedido pedido, long id, String nombre, long precio, BigDecimal iva) {
        Producto producto = new Producto();
        producto.setId(id);
        producto.setNombre(nombre);
        producto.setPrecio(Dinero.de(precio, Moneda.PYG));
        producto.setIva(iva);

        PedidoDetalle detalle = new PedidoDetalle();
        detalle.setProducto(producto);
        detalle.setCantidad(1);
        detalle.setIvaAplicado(iva);
        pedido.agregarDetalle(detalle);
        detalle.fijarPrecio(producto.getPrecio(), null);
    }

    private static Document parsear(String xml) throws Exception {
        DocumentBuilderFactory fabrica = DocumentBuilderFactory.newInstance();
        fabrica.setNamespaceAware(true);
        return fabrica.newDocumentBuilder().parse(
                new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    private static String texto(Document doc, String nombre) {
        return textoNs(doc, NS, nombre);
    }

    private static String textoNs(Document doc, String ns, String nombre) {
        return doc.getElementsByTagNameNS(ns, nombre).item(0).getTextContent().trim();
    }

    private static String texto(Element padre, String nombre) {
        return padre.getElementsByTagNameNS(NS, nombre).item(0).getTextContent().trim();
    }

    private static List<String> hijosElemento(Node padre) {
        List<String> nombres = new ArrayList<>();
        for (Node hijo = padre.getFirstChild(); hijo != null; hijo = hijo.getNextSibling()) {
            if (hijo.getNodeType() == Node.ELEMENT_NODE) {
                nombres.add(hijo.getLocalName());
            }
        }
        return nombres;
    }

    private static Map<String, String> parametros(String url) {
        Map<String, String> resultado = new LinkedHashMap<>();
        String query = url.substring(url.indexOf('?') + 1);
        for (String parte : query.split("&")) {
            String[] par = parte.split("=", 2);
            resultado.put(par[0], par[1]);
        }
        return resultado;
    }

    private static String hex(String texto) {
        StringBuilder salida = new StringBuilder();
        for (byte b : texto.getBytes(StandardCharsets.UTF_8)) {
            salida.append(String.format("%02x", b));
        }
        return salida.toString();
    }

    private static String sha256Hex(String texto) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8));
        StringBuilder salida = new StringBuilder();
        for (byte b : hash) {
            salida.append(String.format("%02x", b));
        }
        return salida.toString();
    }
}
