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
import com.techstore.comun.TipoIdentificacion;
import com.techstore.empresa.EmpresaConfigService;
import com.techstore.empresa.EmpresaProperties;
import com.techstore.facturacion.Factura;
import com.techstore.usuarios.Usuario;
import com.techstore.ventas.PedidoDetalle;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import javax.imageio.ImageIO;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

/**
 * Genera el KuDE (representación gráfica del Documento Electrónico) en PDF,
 * imitando el formato oficial de e-kuatia (Manual Técnico v150, sección 13):
 * datos del emisor y timbrado, datos del receptor, condición de venta y
 * moneda, tabla de ítems con columnas Exentas / Gravadas 5% / Gravadas 10%,
 * liquidación del IVA, CDC en grupos de cuatro y código QR de consulta.
 * El CSC y las URLs se toman de la configuración tipada. Esta clase genera el
 * artefacto localmente; no implementa transporte hacia el SIFEN.
 */
@Service
public class KudeService {

    private static final String NS = "http://ekuatia.set.gov.py/sifen/xsd";
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
    private final SifenProperties sifenProperties;
    private final NumberFormat formato;

    public KudeService(EmpresaConfigService empresaConfig, SifenProperties sifenProperties) {
        this.empresaConfig = empresaConfig;
        this.sifenProperties = sifenProperties;
        this.formato = NumberFormat.getNumberInstance(Locale.forLanguageTag("es-PY"));
        this.formato.setMaximumFractionDigits(2);
        this.formato.setMinimumFractionDigits(0);
    }

    /** Datos vigentes del emisor (editables desde la app). */
    private EmpresaProperties empresa() {
        return empresaConfig.actual();
    }

    public byte[] generar(Factura factura, String cdc, String xmlFirmado) {
        sifenProperties.exigirActivo();
        try {
            // El KuDE codifica exactamente el dCarQR que viaja en el XML. Así no
            // existen dos implementaciones capaces de divergir en un parámetro.
            String urlQr = extraerDCarQr(xmlFirmado);
            byte[] qrPng = generarQrPng(urlQr);
            return construirPdf(factura, cdc, qrPng);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("sifen.kude.fallo", ex.getMessage());
        }
    }

    /**
     * Completa el rDE ya firmado con el grupo fuera de firma exigido por v150.
     * La firma referencia únicamente a DE, por lo que agregar gCamFuFD después
     * de Signature no altera el DigestValue.
     */
    String completarXmlConQr(Factura factura, String cdc, String xmlFirmado) {
        sifenProperties.exigirActivo();
        try {
            String digestValue = extraerDigestValue(xmlFirmado);
            String urlQr = construirUrlQr(factura, cdc, digestValue);
            org.w3c.dom.Document doc = leerXml(xmlFirmado);
            if (doc.getElementsByTagNameNS(NS, "gCamFuFD").getLength() != 0) {
                throw new BusinessException("sifen.kude.fallo", "El XML ya contiene gCamFuFD");
            }
            org.w3c.dom.Element fueraFirma = doc.createElementNS(NS, "gCamFuFD");
            org.w3c.dom.Element caracteresQr = doc.createElementNS(NS, "dCarQR");
            // El serializador convierte los '&' en '&amp;' como pide el manual;
            // al parsear de nuevo se recupera la URL original para generar el QR.
            caracteresQr.setTextContent(urlQr);
            fueraFirma.appendChild(caracteresQr);
            doc.getDocumentElement().appendChild(fueraFirma);
            return serializarXml(doc);
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
        String datos = "nVersion=" + sifenProperties.getVersion().trim()
                + "&Id=" + cdc
                + "&dFeEmiDE=" + fechaHex
                // Con RUC va el número sin dígito verificador; sin RUC, 0 (innominado).
                + "&dRucRec=" + (factura.getReceptorRuc() == null
                        ? "0" : RucParaguayo.numero(factura.getReceptorRuc()))
                + "&dTotGralOpe=" + plano(factura.getTotal())
                + "&dTotIVA=" + plano(iva)
                + "&cItems=" + items
                + "&DigestValue=" + digestHex
                + "&IdCSC=" + sifenProperties.getQr().getIdCsc().trim();

        String hashQr = sha256Hex(datos + sifenProperties.getQr().getCsc().trim());
        return conQuery(sifenProperties.getQr().getBaseUrl(), datos + "&cHashQR=" + hashQr);
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

            // El orden y los bloques son los de la Gráfica N.º 09 del manual
            // técnico (KuDE FE, formato 1 convencional).
            doc.add(titulo());
            doc.add(encabezado(factura));
            doc.add(emisionYReceptor(factura));
            doc.add(tablaItems(factura));
            doc.add(totales(factura));
            doc.add(consultaSifen(cdc, qrPng));
            agregarPie(doc);

            doc.close();
            return salida.toByteArray();
        }
    }

    /** Barra con la denominación del documento, arriba de todo (manual, 13.3). */
    private PdfPTable titulo() {
        PdfPTable tabla = new PdfPTable(1);
        tabla.setWidthPercentage(100);
        PdfPCell celda = new PdfPCell(new Phrase("KuDE de Factura Electrónica", F_NEGRITA));
        celda.setHorizontalAlignment(Element.ALIGN_CENTER);
        celda.setBackgroundColor(GRIS_FONDO);
        celda.setBorderColor(GRIS);
        celda.setPadding(5);
        tabla.addCell(celda);
        return tabla;
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
        emisor.addElement(new Paragraph(empresa().email(), F_NORMAL));
        emisor.addElement(new Paragraph("Actividad económica: " + empresa().actividad(), F_NORMAL));
        tabla.addCell(emisor);

        PdfPCell timbrado = new PdfPCell();
        timbrado.setPadding(8);
        timbrado.setBorderColor(GRIS);
        timbrado.addElement(parrafo("RUC: " + empresa().ruc(), F_NEGRITA, Element.ALIGN_LEFT));
        // El de la factura, no el vigente: reimprimir un KuDE viejo tiene que dar
        // el mismo documento que se emitió ese día.
        timbrado.addElement(parrafo("Timbrado N.º: " + factura.timbradoO(empresa().timbrado()),
                F_NORMAL, Element.ALIGN_LEFT));
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
        boolean contado = factura.esContado();

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
        // Los campos que el modelo del manual muestra vacíos —cuotas, tipo de
        // cambio, documento asociado— solo se imprimen cuando aplican: una
        // etiqueta sin valor al lado no le dice nada a quien recibe la factura.
        izq.addElement(new Paragraph("Moneda: " + factura.getMoneda().codigo(), F_NORMAL));
        tabla.addCell(izq);

        PdfPCell der = new PdfPCell();
        der.setPadding(7);
        der.setBorderColor(GRIS);
        String rucReceptor = factura.getReceptorRuc();
        der.addElement(new Paragraph(rucReceptor == null
                ? "RUC / Documento de Identidad: Sin identificar (Consumidor final)"
                : "RUC / Documento de Identidad: " + rucReceptor, F_NORMAL));
        // No usar pedido.usuario como identidad: en venta directa es el cajero.
        // El KuDE representa el receptor que quedó congelado en la factura.
        der.addElement(new Paragraph("Nombre o Razón Social: " + nombreReceptor(factura), F_NORMAL));
        // Solo si se informaron: antes salían como una raya fija, que ocupa
        // lugar sin decir nada. El manual los da como opcionales.
        if (factura.getReceptorDireccion() != null) {
            der.addElement(new Paragraph("Dirección: " + factura.getReceptorDireccion(), F_NORMAL));
        }
        if (factura.getReceptorTelefono() != null) {
            der.addElement(new Paragraph("Teléfono: " + factura.getReceptorTelefono(), F_NORMAL));
        }
        der.addElement(new Paragraph("Correo Electrónico: " + (factura.getReceptorEmail() != null
                ? factura.getReceptorEmail()
                : cliente.getEmail()), F_NORMAL));
        der.addElement(new Paragraph("Tipo de transacción: Venta de mercadería", F_NORMAL));
        tabla.addCell(der);

        return tabla;
    }

    /** Tabla de ítems con las columnas del formato oficial (valor de venta por tasa). */
    private PdfPTable tablaItems(Factura factura) {
        // Los anchos dan lugar a los rótulos completos: con menos, "Cantidad" y
        // "Descuento" se parten al medio de la palabra.
        PdfPTable tabla = new PdfPTable(new float[]{6, 23, 9, 9, 12, 11, 9, 8, 13});
        tabla.setWidthPercentage(100);
        tabla.setSpacingBefore(6);

        // Cabecera en dos filas, como la Gráfica N.º 09: las tres columnas de
        // importe cuelgan de un "Valor de Venta" que las agrupa.
        String[] simples = {"Cod", "Descripción", "Unidad de medida", "Cantidad",
                "Precio Unitario", "Descuento"};
        for (String t : simples) {
            tabla.addCell(celdaTitulo(t, 1, 2));
        }
        tabla.addCell(celdaTitulo("Valor de Venta", 3, 1));
        for (String t : new String[]{"Exentas", "5%", "10%"}) {
            tabla.addCell(celdaTitulo(t, 1, 1));
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
            tabla.addCell(celda(gs(d.getDescuento()), Element.ALIGN_RIGHT));
            tabla.addCell(celda(tasa == 0 ? gs(monto) : "", Element.ALIGN_RIGHT));
            tabla.addCell(celda(tasa == 5 ? gs(monto) : "", Element.ALIGN_RIGHT));
            tabla.addCell(celda(tasa == 10 ? gs(monto) : "", Element.ALIGN_RIGHT));

            switch (tasa) {
                case 5 -> grav5 = grav5.mas(monto);
                case 10 -> grav10 = grav10.mas(monto);
                default -> exentas = exentas.mas(monto);
            }
        }

        Dinero envio = factura.getPedido().getCostoEnvio();
        if (envio.esPositivo()) {
            int tasa = factura.getPedido().getIvaEnvio() == null ? 0
                    : factura.getPedido().getIvaEnvio().intValue();
            tabla.addCell(celda("ENVIO", Element.ALIGN_CENTER));
            tabla.addCell(celda("Servicio de entrega", Element.ALIGN_LEFT));
            tabla.addCell(celda("UNI", Element.ALIGN_CENTER));
            tabla.addCell(celda("1", Element.ALIGN_CENTER));
            tabla.addCell(celda(gs(envio), Element.ALIGN_RIGHT));
            tabla.addCell(celda("0", Element.ALIGN_RIGHT));
            tabla.addCell(celda(tasa == 0 ? gs(envio) : "", Element.ALIGN_RIGHT));
            tabla.addCell(celda(tasa == 5 ? gs(envio) : "", Element.ALIGN_RIGHT));
            tabla.addCell(celda(tasa == 10 ? gs(envio) : "", Element.ALIGN_RIGHT));
            switch (tasa) {
                case 5 -> grav5 = grav5.mas(envio);
                case 10 -> grav10 = grav10.mas(envio);
                default -> exentas = exentas.mas(envio);
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

    /** Celda de encabezado de la tabla de ítems, con su ocupación de filas/columnas. */
    private PdfPCell celdaTitulo(String texto, int columnas, int filas) {
        PdfPCell c = new PdfPCell(new Phrase(texto, F_NEGRITA));
        c.setColspan(columnas);
        c.setRowspan(filas);
        c.setBackgroundColor(GRIS_FONDO);
        c.setHorizontalAlignment(Element.ALIGN_CENTER);
        c.setVerticalAlignment(Element.ALIGN_MIDDLE);
        c.setPadding(4);
        c.setBorderColor(GRIS);
        return c;
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
        Dinero envio = factura.getPedido().getCostoEnvio();
        int tasaEnvio = factura.getPedido().getIvaEnvio() == null ? 0
                : factura.getPedido().getIvaEnvio().intValue();
        if (tasaEnvio == 5) grav5 = grav5.mas(envio);
        if (tasaEnvio == 10) grav10 = grav10.mas(envio);
        Dinero iva5 = grav5.ivaIncluido(TASA_5);
        Dinero iva10 = grav10.ivaIncluido(TASA_10);

        PdfPTable tabla = new PdfPTable(1);
        tabla.setWidthPercentage(100);

        // Las filas y sus rótulos son los del manual (13.4.3): total de la
        // operación, total en guaraníes y liquidación del IVA.
        tabla.addCell(lineaTotal("TOTAL DE LA OPERACIÓN:   %s %s"
                .formatted(factura.getMoneda().simbolo(), gs(factura.getTotal())), F_NEGRITA));
        tabla.addCell(lineaTotal("TOTAL EN %s:   %s".formatted(
                factura.getMoneda().nombrePlural().toUpperCase(), gs(factura.getTotal())), F_NEGRITA));
        tabla.addCell(lineaTotal(
                "LIQUIDACIÓN IVA:   (5%) " + gs(iva5) + "     (10%) " + gs(iva10)
                        + "     TOTAL IVA: " + gs(iva5.mas(iva10)), F_NORMAL));
        // El importe en letras no está en el modelo del manual, pero el manual
        // permite agregar campos y en Paraguay la factura lo lleva por costumbre.
        tabla.addCell(lineaTotal(
                "SON: %s.-".formatted(capitalizar(NumeroALetras.enLetras(factura.getTotal()))), F_NORMAL));
        return tabla;
    }

    /**
     * Bloque de consulta en el SIFEN: el QR a la izquierda y, a su derecha, la
     * leyenda con la dirección del portal y el CDC en once grupos de cuatro.
     *
     * <p>Va encuadrado y en ese orden porque así lo muestra la Gráfica N.º 09
     * del manual. Antes el QR salía centrado, del tamaño de un cuarto de hoja y
     * suelto al pie, sin recuadro que lo separara del resto.
     */
    private PdfPTable consultaSifen(String cdc, byte[] qrPng) throws Exception {
        PdfPTable tabla = new PdfPTable(new float[]{18, 82});
        tabla.setWidthPercentage(100);
        tabla.setSpacingBefore(6);

        Image qr = Image.getInstance(qrPng);
        qr.scaleToFit(80, 80);
        PdfPCell celdaQr = new PdfPCell(qr, false);
        celdaQr.setPadding(6);
        celdaQr.setBorderColor(GRIS);
        celdaQr.setHorizontalAlignment(Element.ALIGN_CENTER);
        celdaQr.setVerticalAlignment(Element.ALIGN_MIDDLE);
        tabla.addCell(celdaQr);

        PdfPCell texto = new PdfPCell();
        texto.setPadding(6);
        texto.setBorderColor(GRIS);
        texto.setVerticalAlignment(Element.ALIGN_MIDDLE);
        texto.addElement(new Paragraph(
                "Consulte la validez de esta Factura Electrónica con el número de CDC impreso abajo en:", F_CHICA));
        texto.addElement(new Paragraph(sifenProperties.getQr().getConsultaPublicaUrl(), F_CHICA));

        // El CDC va resaltado: es el dato que alguien va a tipear en el portal.
        PdfPTable caja = new PdfPTable(1);
        caja.setWidthPercentage(100);
        caja.setSpacingBefore(4);
        PdfPCell celdaCdc = new PdfPCell(
                new Phrase("CDC: " + CodigoControl.enGruposDeCuatro(cdc), F_NEGRITA));
        celdaCdc.setBackgroundColor(GRIS_FONDO);
        celdaCdc.setBorderColor(GRIS);
        celdaCdc.setPadding(4);
        caja.addCell(celdaCdc);
        texto.addElement(caja);

        tabla.addCell(texto);
        return tabla;
    }

    /** Leyendas legales y espacio de información del emisor (manual, 13.4.5). */
    private void agregarPie(Document doc) throws Exception {
        Paragraph representacion = parrafo(
                "ESTE DOCUMENTO ES UNA REPRESENTACIÓN GRÁFICA DE UN DOCUMENTO ELECTRÓNICO (XML)",
                F_MINI, Element.ALIGN_LEFT);
        representacion.setSpacingBefore(6);
        doc.add(representacion);

        doc.add(parrafo("Información de interés del facturador electrónico emisor.", F_CHICA, Element.ALIGN_LEFT));
        doc.add(parrafo(
                "Si su documento electrónico presenta algún error, podrá solicitar la modificación dentro de "
                        + "las 72 horas siguientes de la emisión de este comprobante.", F_CHICA, Element.ALIGN_LEFT));

        String leyenda = sifenProperties.getModo() == SifenProperties.Modo.TEST
                ? "Documento de TEST — no transmitido al SIFEN y sin valor tributario."
                : "Documento generado localmente — esta aplicación no implementa transmisión al SIFEN.";
        Paragraph aviso = parrafo(leyenda, F_MINI, Element.ALIGN_LEFT);
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
        iva = iva.mas(factura.getPedido().getCostoEnvio().ivaIncluido(factura.getPedido().getIvaEnvio()));
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
        var doc = leerXml(xmlFirmado);
        var nodos = doc.getElementsByTagNameNS("http://www.w3.org/2000/09/xmldsig#", "DigestValue");
        if (nodos.getLength() == 0) {
            throw new BusinessException("sifen.xml.sin.firma");
        }
        return nodos.item(0).getTextContent().trim();
    }

    String extraerDCarQr(String xmlCompleto) throws Exception {
        var doc = leerXml(xmlCompleto);
        var nodos = doc.getElementsByTagNameNS(NS, "dCarQR");
        if (nodos.getLength() != 1) {
            throw new BusinessException("sifen.kude.fallo", "El XML no contiene un único dCarQR");
        }
        return nodos.item(0).getTextContent().trim();
    }

    private static org.w3c.dom.Document leerXml(String xml) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
        dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        dbf.setXIncludeAware(false);
        dbf.setExpandEntityReferences(false);
        dbf.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        dbf.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        return dbf.newDocumentBuilder()
                .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    private static String serializarXml(org.w3c.dom.Document doc) throws Exception {
        TransformerFactory factory = TransformerFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
        var transformer = factory.newTransformer();
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        StringWriter salida = new StringWriter();
        transformer.transform(new DOMSource(doc), new StreamResult(salida));
        return salida.toString();
    }

    static String nombreReceptor(Factura factura) {
        if (factura.getReceptorNombre() != null && !factura.getReceptorNombre().isBlank()) {
            return factura.getReceptorNombre().trim();
        }
        TipoIdentificacion tipo = factura.getReceptorTipoDocumento();
        return tipo == null ? TipoIdentificacion.SinNombre.etiqueta() : tipo.etiqueta();
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

    private static String conQuery(String base, String query) {
        String limpia = base.trim();
        if (limpia.endsWith("?") || limpia.endsWith("&")) {
            return limpia + query;
        }
        return limpia + (limpia.contains("?") ? "&" : "?") + query;
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
