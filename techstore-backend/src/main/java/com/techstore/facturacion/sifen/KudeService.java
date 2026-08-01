package com.techstore.facturacion.sifen;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.techstore.comun.BusinessException;
import com.techstore.comun.Dinero;
import com.techstore.comun.LogoPdf;
import com.techstore.comun.NumeroALetras;
import com.techstore.comun.RucParaguayo;
import com.techstore.empresa.EmpresaConfigService;
import com.techstore.empresa.EmpresaProperties;
import com.techstore.facturacion.Factura;
import com.techstore.usuarios.Usuario;
import com.techstore.ventas.PedidoDetalle;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import javax.imageio.ImageIO;
import javax.xml.parsers.DocumentBuilderFactory;

/**
 * Genera el KuDE (representación gráfica del Documento Electrónico) en PDF,
 * imitando el formato oficial de e-kuatia (Manual Técnico v150, sección 13):
 * datos del emisor y timbrado, datos del receptor, condición de venta y
 * moneda, tabla de ítems con columnas Exentas / Gravadas 5% / Gravadas 10%,
 * liquidación del IVA, CDC en grupos de cuatro y código QR de consulta.
 * Demostrativo: usa un CSC de prueba y no se transmite al SIFEN.
 */
@Service
public class KudeService {

    // URL de consulta del ambiente de TEST del SIFEN.
    private static final String URL_QR = "https://ekuatia.set.gov.py/consultas-test/qr?";
    private static final String URL_CONSULTA = "https://ekuatia.set.gov.py/consultas/";
    // Código de Seguridad del Contribuyente (secreto entregado por la SET). De PRUEBA.
    private static final String CSC = "ABCD0000000000000000000000000000";
    private static final String ID_CSC = "0001";
    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final BigDecimal TASA_5 = BigDecimal.valueOf(5);
    private static final BigDecimal TASA_10 = BigDecimal.TEN;

    private static final Color NEGRO = new Color(20, 20, 20);
    private static final Color GRIS = new Color(120, 120, 120);
    private static final Color GRIS_FONDO = new Color(235, 235, 235);
    private static final Font F_EMPRESA = new Font(Font.HELVETICA, 12, Font.BOLD, NEGRO);
    private static final Font F_TITULO = new Font(Font.HELVETICA, 12, Font.BOLD, NEGRO);
    private static final Font F_NORMAL = new Font(Font.HELVETICA, 9, Font.NORMAL, NEGRO);
    private static final Font F_NEGRITA = new Font(Font.HELVETICA, 9, Font.BOLD, NEGRO);
    private static final Font F_MINI = new Font(Font.HELVETICA, 7, Font.NORMAL, NEGRO);
    private static final Font F_CHICA = new Font(Font.HELVETICA, 8, Font.NORMAL, GRIS);

    private final EmpresaConfigService empresaConfig;
    private final NumberFormat formato;

    public KudeService(EmpresaConfigService empresaConfig) {
        this.empresaConfig = empresaConfig;
        this.formato = NumberFormat.getNumberInstance(Locale.forLanguageTag("es-PY"));
        this.formato.setMaximumFractionDigits(2);
        this.formato.setMinimumFractionDigits(0);
    }

    /** Datos vigentes del emisor (editables desde la app). */
    private EmpresaProperties empresa() {
        return empresaConfig.actual();
    }

    public byte[] generar(Factura factura, String cdc, String xmlFirmado) {
        try {
            String digestValue = extraerDigestValue(xmlFirmado);
            String urlQr = construirUrlQr(factura, cdc, digestValue);
            byte[] qrPng = generarQrPng(urlQr);
            return construirPdf(factura, cdc, qrPng);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("sifen.kude.fallo", ex.getMessage());
        }
    }

    private String construirUrlQr(Factura factura, String cdc, String digestValue) throws Exception {
        Dinero iva = ivaTotal(factura);
        String fechaHex = hex(factura.getFecha().format(DT));
        String digestHex = hex(digestValue);
        long items = factura.getPedido().getDetalles().size();

        // Parámetros a hashear (sin cHashQR), en el orden del manual.
        String datos = "nVersion=150"
                + "&Id=" + cdc
                + "&dFeEmiDE=" + fechaHex
                // Con RUC va el número sin dígito verificador; sin RUC, 0 (innominado).
                + "&dRucRec=" + (factura.getReceptorRuc() == null
                        ? "0" : RucParaguayo.numero(factura.getReceptorRuc()))
                + "&dTotGralOpe=" + plano(factura.getTotal())
                + "&dTotIVA=" + plano(iva)
                + "&cItems=" + items
                + "&DigestValue=" + digestHex
                + "&IdCSC=" + ID_CSC;

        String hashQr = sha256Hex(datos + CSC);
        return URL_QR + datos + "&cHashQR=" + hashQr;
    }

    private byte[] generarQrPng(String contenido) throws Exception {
        BitMatrix matrix = new QRCodeWriter().encode(contenido, BarcodeFormat.QR_CODE, 260, 260);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(MatrixToImageWriter.toBufferedImage(matrix), "PNG", baos);
        return baos.toByteArray();
    }

    private byte[] construirPdf(Factura factura, String cdc, byte[] qrPng) throws Exception {
        try (ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 36, 36, 36, 36);
            PdfWriter.getInstance(doc, salida);
            doc.open();

            doc.add(encabezado(factura));
            doc.add(emisionYReceptor(factura));
            doc.add(tablaItems(factura));
            doc.add(totales(factura));
            agregarPie(doc, cdc, qrPng);

            doc.close();
            return salida.toByteArray();
        }
    }

    /** Cabecera: datos del emisor a la izquierda, timbrado y número a la derecha. */
    private PdfPTable encabezado(Factura factura) {
        PdfPTable tabla = new PdfPTable(new float[]{58, 42});
        tabla.setWidthPercentage(100);

        PdfPCell emisor = new PdfPCell();
        emisor.setPadding(8);
        emisor.setBorderColor(GRIS);
        // Isotipo encima del nombre; si no se pudo cargar, la cabecera sigue igual.
        Image logo = LogoPdf.imagen(30f);
        if (logo != null) {
            Paragraph marca = new Paragraph();
            marca.add(new Chunk(logo, 0, -8));
            marca.setSpacingAfter(2);
            emisor.addElement(marca);
        }
        emisor.addElement(new Paragraph(empresa().nombre(), F_EMPRESA));
        emisor.addElement(new Paragraph("Sistema de Facturación Electrónica", F_CHICA));
        emisor.addElement(new Paragraph(empresa().direccion(), F_NORMAL));
        emisor.addElement(new Paragraph("Ciudad: " + empresa().ciudad(), F_NORMAL));
        emisor.addElement(new Paragraph("Teléfono: " + empresa().telefono(), F_NORMAL));
        emisor.addElement(new Paragraph("Actividad económica: " + empresa().actividad(), F_NORMAL));
        tabla.addCell(emisor);

        PdfPCell timbrado = new PdfPCell();
        timbrado.setPadding(8);
        timbrado.setBorderColor(GRIS);
        timbrado.addElement(parrafo("RUC: " + empresa().ruc(), F_NEGRITA, Element.ALIGN_LEFT));
        timbrado.addElement(parrafo("Timbrado N.º: " + empresa().timbrado(), F_NORMAL, Element.ALIGN_LEFT));
        timbrado.addElement(parrafo("Fecha de Inicio de Vigencia: " + fechaVigencia(), F_NORMAL, Element.ALIGN_LEFT));
        timbrado.addElement(parrafo(" ", F_MINI, Element.ALIGN_LEFT));
        timbrado.addElement(parrafo("FACTURA ELECTRÓNICA", F_TITULO, Element.ALIGN_CENTER));
        timbrado.addElement(parrafo(factura.getNumero(), F_NEGRITA, Element.ALIGN_CENTER));
        tabla.addCell(timbrado);

        return tabla;
    }

    /** Datos de emisión/condición a la izquierda y datos del receptor a la derecha. */
    private PdfPTable emisionYReceptor(Factura factura) {
        Usuario cliente = factura.getPedido().getUsuario();
        boolean contado = !"CREDITO".equalsIgnoreCase(factura.getTipo());

        PdfPTable tabla = new PdfPTable(new float[]{50, 50});
        tabla.setWidthPercentage(100);
        tabla.setSpacingBefore(6);

        PdfPCell izq = new PdfPCell();
        izq.setPadding(7);
        izq.setBorderColor(GRIS);
        izq.addElement(new Paragraph("Fecha y hora de emisión: " + factura.getFecha().format(FECHA_HORA), F_NORMAL));
        izq.addElement(new Paragraph(
                "Condición de venta:   Contado [" + (contado ? "X" : " ") + "]   Crédito [" + (contado ? " " : "X") + "]",
                F_NORMAL));
        izq.addElement(new Paragraph("Moneda: %s (%s)".formatted(
                factura.getMoneda().codigo(), factura.getMoneda().nombre()), F_NORMAL));
        izq.addElement(new Paragraph("Tipo de cambio: —", F_NORMAL));
        izq.addElement(new Paragraph("Tipo de transacción: Venta de mercadería", F_NORMAL));
        tabla.addCell(izq);

        PdfPCell der = new PdfPCell();
        der.setPadding(7);
        der.setBorderColor(GRIS);
        String rucReceptor = factura.getReceptorRuc();
        der.addElement(new Paragraph(rucReceptor == null
                ? "RUC / Documento de Identidad: Sin identificar (Consumidor final)"
                : "RUC / Documento de Identidad: " + rucReceptor, F_NORMAL));
        der.addElement(new Paragraph("Nombre o Razón Social: " + (rucReceptor == null
                ? cliente.getNombre() + " " + cliente.getApellido()
                : factura.getReceptorNombre()), F_NORMAL));
        der.addElement(new Paragraph("Dirección: —", F_NORMAL));
        der.addElement(new Paragraph("Teléfono: —", F_NORMAL));
        der.addElement(new Paragraph("Correo Electrónico: " + (factura.getReceptorEmail() != null
                ? factura.getReceptorEmail()
                : cliente.getEmail()), F_NORMAL));
        tabla.addCell(der);

        return tabla;
    }

    /** Tabla de ítems con las columnas del formato oficial (valor de venta por tasa). */
    private PdfPTable tablaItems(Factura factura) {
        PdfPTable tabla = new PdfPTable(new float[]{7, 25, 9, 8, 12, 9, 9, 9, 12});
        tabla.setWidthPercentage(100);
        tabla.setSpacingBefore(6);

        String[] titulos = {"Cód.", "Descripción", "Unidad", "Cant.", "Precio Unit.",
                "Descuento", "Exentas", "Grav. 5%", "Grav. 10%"};
        for (String t : titulos) {
            PdfPCell c = new PdfPCell(new Phrase(t, F_NEGRITA));
            c.setBackgroundColor(GRIS_FONDO);
            c.setHorizontalAlignment(Element.ALIGN_CENTER);
            c.setPadding(4);
            c.setBorderColor(GRIS);
            tabla.addCell(c);
        }

        Dinero exentas = Dinero.cero(factura.getMoneda());
        Dinero grav5 = Dinero.cero(factura.getMoneda());
        Dinero grav10 = Dinero.cero(factura.getMoneda());
        for (PedidoDetalle d : factura.getPedido().getDetalles()) {
            int tasa = d.getIvaAplicado() == null ? 0 : d.getIvaAplicado().intValue();
            Dinero monto = d.getSubtotal();

            tabla.addCell(celda(String.format("%04d", d.getProducto().getId()), Element.ALIGN_CENTER));
            tabla.addCell(celda(d.getProducto().getNombre(), Element.ALIGN_LEFT));
            tabla.addCell(celda("UNI", Element.ALIGN_CENTER));
            tabla.addCell(celda(String.valueOf(d.getCantidad()), Element.ALIGN_CENTER));
            tabla.addCell(celda(gs(d.getPrecioUnitario()), Element.ALIGN_RIGHT));
            tabla.addCell(celda("0", Element.ALIGN_RIGHT));
            tabla.addCell(celda(tasa == 0 ? gs(monto) : "", Element.ALIGN_RIGHT));
            tabla.addCell(celda(tasa == 5 ? gs(monto) : "", Element.ALIGN_RIGHT));
            tabla.addCell(celda(tasa == 10 ? gs(monto) : "", Element.ALIGN_RIGHT));

            switch (tasa) {
                case 5 -> grav5 = grav5.mas(monto);
                case 10 -> grav10 = grav10.mas(monto);
                default -> exentas = exentas.mas(monto);
            }
        }

        PdfPCell etiqueta = new PdfPCell(new Phrase("SUBTOTAL", F_NEGRITA));
        etiqueta.setColspan(6);
        etiqueta.setHorizontalAlignment(Element.ALIGN_RIGHT);
        etiqueta.setPadding(4);
        etiqueta.setBorderColor(GRIS);
        etiqueta.setBackgroundColor(GRIS_FONDO);
        tabla.addCell(etiqueta);
        for (Dinero col : new Dinero[]{exentas, grav5, grav10}) {
            PdfPCell c = new PdfPCell(new Phrase(gs(col), F_NEGRITA));
            c.setHorizontalAlignment(Element.ALIGN_RIGHT);
            c.setPadding(4);
            c.setBorderColor(GRIS);
            c.setBackgroundColor(GRIS_FONDO);
            tabla.addCell(c);
        }

        return tabla;
    }

    /** Totales de la operación, importe en letras y liquidación del IVA. */
    private PdfPTable totales(Factura factura) {
        Dinero grav5 = Dinero.cero(factura.getMoneda());
        Dinero grav10 = Dinero.cero(factura.getMoneda());
        for (PedidoDetalle d : factura.getPedido().getDetalles()) {
            int tasa = d.getIvaAplicado() == null ? 0 : d.getIvaAplicado().intValue();
            switch (tasa) {
                case 5 -> grav5 = grav5.mas(d.getSubtotal());
                case 10 -> grav10 = grav10.mas(d.getSubtotal());
                default -> { }
            }
        }
        Dinero iva5 = grav5.ivaIncluido(TASA_5);
        Dinero iva10 = grav10.ivaIncluido(TASA_10);

        PdfPTable tabla = new PdfPTable(1);
        tabla.setWidthPercentage(100);

        tabla.addCell(lineaTotal("TOTAL DE LA OPERACIÓN:   %s %s"
                .formatted(factura.getMoneda().simbolo(), gs(factura.getTotal())), F_NEGRITA));
        tabla.addCell(lineaTotal(
                "TOTAL EN %s (EN LETRAS): %s.-".formatted(
                        factura.getMoneda().nombrePlural().toUpperCase(),
                        capitalizar(NumeroALetras.enLetras(factura.getTotal()))),
                F_NORMAL));
        tabla.addCell(lineaTotal(
                "LIQUIDACIÓN DEL IVA:   (5%) " + gs(iva5) + "     (10%) " + gs(iva10)
                        + "     TOTAL IVA: " + gs(iva5.mas(iva10)), F_NORMAL));
        return tabla;
    }

    /** QR de consulta, CDC en grupos de cuatro y leyendas legales. */
    private void agregarPie(Document doc, String cdc, byte[] qrPng) throws Exception {
        Image qr = Image.getInstance(qrPng);
        qr.setAlignment(Element.ALIGN_CENTER);
        qr.scaleToFit(130, 130);
        qr.setSpacingBefore(12);
        doc.add(qr);

        doc.add(parrafo(
                "Consulte la validez de esta Factura Electrónica con el número de CDC impreso abajo en:",
                F_CHICA, Element.ALIGN_CENTER));
        doc.add(parrafo(URL_CONSULTA, F_CHICA, Element.ALIGN_CENTER));

        Paragraph cdcP = parrafo("CDC: " + CodigoControl.enGruposDeCuatro(cdc), F_NEGRITA, Element.ALIGN_CENTER);
        cdcP.setSpacingBefore(4);
        cdcP.setSpacingAfter(6);
        doc.add(cdcP);

        doc.add(parrafo(
                "ESTE DOCUMENTO ES UNA REPRESENTACIÓN GRÁFICA DE UN DOCUMENTO ELECTRÓNICO (XML)",
                F_MINI, Element.ALIGN_CENTER));
        doc.add(parrafo(
                "Información de interés del facturador electrónico emisor: si su documento electrónico presenta "
                        + "algún error, podrá solicitar la modificación dentro de las 72 horas siguientes de la "
                        + "emisión de este comprobante.", F_MINI, Element.ALIGN_CENTER));

        Paragraph aviso = parrafo(
                "Documento demostrativo — firmado con certificado de PRUEBA, no transmitido al SIFEN.",
                F_CHICA, Element.ALIGN_CENTER);
        aviso.setSpacingBefore(6);
        doc.add(aviso);
    }

    // ---- utilitarios ----

    /**
     * El IVA que va en el QR. Antes se calculaba acá con dos decimales mientras
     * la liquidación impresa más abajo lo hacía con cero, así que el mismo
     * documento podía declarar dos IVA distintos. Ahora los dos salen de
     * {@link Dinero#ivaIncluido}.
     */
    private Dinero ivaTotal(Factura factura) {
        Dinero iva = Dinero.cero(factura.getMoneda());
        for (PedidoDetalle d : factura.getPedido().getDetalles()) {
            iva = iva.mas(d.getSubtotal().ivaIncluido(d.getIvaAplicado()));
        }
        return iva;
    }

    private String fechaVigencia() {
        try {
            return LocalDate.parse(empresa().timbradoInicioVigencia()).format(FECHA);
        } catch (Exception ex) {
            return empresa().timbradoInicioVigencia();
        }
    }

    private String extraerDigestValue(String xmlFirmado) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        var doc = dbf.newDocumentBuilder()
                .parse(new ByteArrayInputStream(xmlFirmado.getBytes(StandardCharsets.UTF_8)));
        var nodos = doc.getElementsByTagNameNS("http://www.w3.org/2000/09/xmldsig#", "DigestValue");
        if (nodos.getLength() == 0) {
            throw new BusinessException("sifen.xml.sin.firma");
        }
        return nodos.item(0).getTextContent().trim();
    }

    private PdfPCell celda(String texto, int alineacion) {
        PdfPCell c = new PdfPCell(new Phrase(texto, F_NORMAL));
        c.setHorizontalAlignment(alineacion);
        c.setPadding(4);
        c.setBorderColor(GRIS);
        return c;
    }

    private PdfPCell lineaTotal(String texto, Font fuente) {
        PdfPCell c = new PdfPCell(new Phrase(texto, fuente));
        c.setHorizontalAlignment(Element.ALIGN_RIGHT);
        c.setPadding(5);
        c.setBorderColor(GRIS);
        return c;
    }

    private static Paragraph parrafo(String texto, Font fuente, int alineacion) {
        Paragraph p = new Paragraph(texto, fuente);
        p.setAlignment(alineacion);
        return p;
    }

    private static String hex(String texto) {
        StringBuilder sb = new StringBuilder();
        for (byte b : texto.getBytes(StandardCharsets.UTF_8)) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static String sha256Hex(String texto) throws Exception {
        java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
        byte[] hash = md.digest(texto.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private String plano(Dinero valor) {
        return valor.monto().stripTrailingZeros().toPlainString();
    }

    private String gs(Dinero valor) {
        return formato.format(valor.monto());
    }

    private static String capitalizar(String texto) {
        return texto.isEmpty() ? texto : Character.toUpperCase(texto.charAt(0)) + texto.substring(1);
    }
}
