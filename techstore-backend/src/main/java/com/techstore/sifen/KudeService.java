package com.techstore.sifen;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
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
import com.techstore.config.EmpresaProperties;
import com.techstore.entity.Factura;
import com.techstore.entity.PedidoDetalle;
import com.techstore.exception.BusinessException;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import javax.xml.parsers.DocumentBuilderFactory;
import java.awt.Color;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Genera el KuDE (representación gráfica del Documento Electrónico) en PDF,
 * con el CDC en grupos de cuatro y el código QR de consulta del SIFEN
 * (Manual Técnico v150, sección 13). Demostrativo: usa un CSC de prueba.
 */
@Service
public class KudeService {

    // URL de consulta del ambiente de TEST del SIFEN.
    private static final String URL_QR = "https://ekuatia.set.gov.py/consultas-test/qr?";
    // Código de Seguridad del Contribuyente (secreto entregado por la SET). De PRUEBA.
    private static final String CSC = "ABCD0000000000000000000000000000";
    private static final String ID_CSC = "0001";
    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final BigDecimal CIEN = BigDecimal.valueOf(100);

    private static final Color NEGRO = new Color(20, 20, 20);
    private static final Color GRIS = new Color(120, 120, 120);
    private static final Font F_EMPRESA = new Font(Font.HELVETICA, 12, Font.BOLD, NEGRO);
    private static final Font F_TITULO = new Font(Font.HELVETICA, 12, Font.BOLD, NEGRO);
    private static final Font F_NORMAL = new Font(Font.HELVETICA, 9, Font.NORMAL, NEGRO);
    private static final Font F_NEGRITA = new Font(Font.HELVETICA, 9, Font.BOLD, NEGRO);
    private static final Font F_CHICA = new Font(Font.HELVETICA, 8, Font.NORMAL, GRIS);

    private final EmpresaProperties empresa;
    private final NumberFormat formato;

    public KudeService(EmpresaProperties empresa) {
        this.empresa = empresa;
        this.formato = NumberFormat.getNumberInstance(Locale.forLanguageTag("es-PY"));
        this.formato.setMaximumFractionDigits(2);
        this.formato.setMinimumFractionDigits(0);
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
            throw new BusinessException("No se pudo generar el KuDE: " + ex.getMessage());
        }
    }

    private String construirUrlQr(Factura factura, String cdc, String digestValue) throws Exception {
        BigDecimal iva = ivaTotal(factura);
        String fechaHex = hex(factura.getFecha().format(DT));
        String digestHex = hex(digestValue);
        long items = factura.getPedido().getDetalles().size();

        // Parámetros a hashear (sin cHashQR), en el orden del manual.
        String datos = "nVersion=150"
                + "&Id=" + cdc
                + "&dFeEmiDE=" + fechaHex
                + "&dRucRec=0"
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
            Document doc = new Document(PageSize.A4, 40, 40, 40, 40);
            PdfWriter.getInstance(doc, salida);
            doc.open();

            doc.add(new Paragraph(empresa.nombre(), F_EMPRESA));
            doc.add(new Paragraph("RUC: " + empresa.ruc() + "   —   Timbrado N.º " + empresa.timbrado(), F_NORMAL));
            doc.add(new Paragraph(empresa.direccion() + " · " + empresa.ciudad(), F_CHICA));

            Paragraph titulo = new Paragraph("KuDE — FACTURA ELECTRÓNICA", F_TITULO);
            titulo.setAlignment(Element.ALIGN_CENTER);
            titulo.setSpacingBefore(8);
            doc.add(titulo);
            Paragraph num = new Paragraph(factura.getNumero() + "   ·   " + factura.getFecha().format(DT), F_NORMAL);
            num.setAlignment(Element.ALIGN_CENTER);
            doc.add(num);

            Paragraph cdcP = new Paragraph("CDC: " + CodigoControl.enGruposDeCuatro(cdc), F_NEGRITA);
            cdcP.setAlignment(Element.ALIGN_CENTER);
            cdcP.setSpacingBefore(6);
            cdcP.setSpacingAfter(8);
            doc.add(cdcP);

            doc.add(new Paragraph(
                    "Cliente: " + factura.getPedido().getUsuario().getNombre() + " "
                            + factura.getPedido().getUsuario().getApellido(), F_NORMAL));

            // Tabla de ítems.
            PdfPTable tabla = new PdfPTable(new float[]{12, 46, 20, 22});
            tabla.setWidthPercentage(100);
            tabla.setSpacingBefore(8);
            for (String t : new String[]{"Cant.", "Descripción", "Precio unit.", "Total"}) {
                PdfPCell c = new PdfPCell(new Phrase(t, F_NEGRITA));
                c.setBackgroundColor(new Color(235, 235, 235));
                c.setPadding(5);
                c.setBorderColor(GRIS);
                tabla.addCell(c);
            }
            for (PedidoDetalle d : factura.getPedido().getDetalles()) {
                tabla.addCell(celda(String.valueOf(d.getCantidad()), Element.ALIGN_CENTER));
                tabla.addCell(celda(d.getProducto().getNombre(), Element.ALIGN_LEFT));
                tabla.addCell(celda(gs(d.getPrecioUnitario()), Element.ALIGN_RIGHT));
                tabla.addCell(celda(gs(d.getSubtotal()), Element.ALIGN_RIGHT));
            }
            doc.add(tabla);

            Paragraph tot = new Paragraph(
                    "TOTAL: Gs. " + gs(factura.getTotal()) + "   ·   IVA: Gs. " + gs(ivaTotal(factura)), F_NEGRITA);
            tot.setAlignment(Element.ALIGN_RIGHT);
            tot.setSpacingBefore(6);
            doc.add(tot);

            // QR + leyenda.
            Image qr = Image.getInstance(qrPng);
            qr.setAlignment(Element.ALIGN_CENTER);
            qr.scaleToFit(140, 140);
            qr.setSpacingBefore(14);
            doc.add(qr);
            Paragraph leyenda = new Paragraph(
                    "Consulte la validez de esta Factura Electrónica con el CDC en ekuatia.set.gov.py",
                    F_CHICA);
            leyenda.setAlignment(Element.ALIGN_CENTER);
            doc.add(leyenda);
            Paragraph aviso = new Paragraph(
                    "Documento demostrativo — firmado con certificado de PRUEBA, no transmitido al SIFEN.", F_CHICA);
            aviso.setAlignment(Element.ALIGN_CENTER);
            doc.add(aviso);

            doc.close();
            return salida.toByteArray();
        }
    }

    private BigDecimal ivaTotal(Factura factura) {
        BigDecimal iva = BigDecimal.ZERO;
        for (PedidoDetalle d : factura.getPedido().getDetalles()) {
            BigDecimal tasa = d.getIvaAplicado() == null ? BigDecimal.ZERO : d.getIvaAplicado();
            if (tasa.signum() > 0) {
                iva = iva.add(d.getSubtotal().multiply(tasa).divide(CIEN.add(tasa), 2, RoundingMode.HALF_UP));
            }
        }
        return iva;
    }

    private String extraerDigestValue(String xmlFirmado) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        var doc = dbf.newDocumentBuilder()
                .parse(new ByteArrayInputStream(xmlFirmado.getBytes(StandardCharsets.UTF_8)));
        var nodos = doc.getElementsByTagNameNS("http://www.w3.org/2000/09/xmldsig#", "DigestValue");
        if (nodos.getLength() == 0) {
            throw new BusinessException("El XML no contiene firma (DigestValue)");
        }
        return nodos.item(0).getTextContent().trim();
    }

    private PdfPCell celda(String texto, int alineacion) {
        PdfPCell c = new PdfPCell(new Phrase(texto, F_NORMAL));
        c.setHorizontalAlignment(alineacion);
        c.setPadding(5);
        c.setBorderColor(GRIS);
        return c;
    }

    private static String hex(String texto) {
        StringBuilder sb = new StringBuilder();
        for (byte b : texto.getBytes(StandardCharsets.UTF_8)) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static String sha256Hex(String texto) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] hash = md.digest(texto.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private String plano(BigDecimal valor) {
        return valor.stripTrailingZeros().toPlainString();
    }

    private String gs(BigDecimal valor) {
        return formato.format(valor);
    }
}
