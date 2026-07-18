package com.techstore.service;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.pdf.draw.LineSeparator;
import com.techstore.entity.Factura;
import com.techstore.entity.PedidoDetalle;
import com.techstore.entity.Usuario;
import com.techstore.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Genera el PDF imprimible de una factura con OpenPDF (licencia libre).
 */
@Service
public class FacturaPdfService {

    private static final Color AZUL_OSCURO = new Color(15, 23, 42);
    private static final Color AZUL_PRIMARIO = new Color(15, 102, 230);
    private static final Color GRIS_CLARO = new Color(241, 245, 249);
    private static final Color GRIS_BORDE = new Color(203, 213, 225);

    private static final Font F_TITULO = new Font(Font.HELVETICA, 20, Font.BOLD, AZUL_PRIMARIO);
    private static final Font F_EMPRESA = new Font(Font.HELVETICA, 14, Font.BOLD, AZUL_OSCURO);
    private static final Font F_NORMAL = new Font(Font.HELVETICA, 10, Font.NORMAL, AZUL_OSCURO);
    private static final Font F_GRIS = new Font(Font.HELVETICA, 9, Font.NORMAL, new Color(100, 116, 139));
    private static final Font F_NEGRITA = new Font(Font.HELVETICA, 10, Font.BOLD, AZUL_OSCURO);
    private static final Font F_ENCABEZADO_TABLA = new Font(Font.HELVETICA, 10, Font.BOLD, Color.WHITE);
    private static final Font F_TOTAL = new Font(Font.HELVETICA, 12, Font.BOLD, AZUL_PRIMARIO);

    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final String empresaNombre;
    private final String empresaRuc;
    private final NumberFormat formatoNumero;

    public FacturaPdfService(
            @Value("${empresa.nombre}") String empresaNombre,
            @Value("${empresa.ruc}") String empresaRuc) {
        this.empresaNombre = empresaNombre;
        this.empresaRuc = empresaRuc;
        this.formatoNumero = NumberFormat.getNumberInstance(Locale.forLanguageTag("es-PY"));
        this.formatoNumero.setMaximumFractionDigits(2);
        this.formatoNumero.setMinimumFractionDigits(0);
    }

    public byte[] generar(Factura factura) {
        try (ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            Document documento = new Document(PageSize.A4, 40, 40, 40, 40);
            PdfWriter.getInstance(documento, salida);
            documento.open();

            agregarEncabezado(documento, factura);
            agregarDatos(documento, factura);
            agregarItems(documento, factura);
            agregarTotales(documento, factura);
            agregarPie(documento);

            documento.close();
            return salida.toByteArray();
        } catch (DocumentException | java.io.IOException ex) {
            throw new BusinessException("No se pudo generar el PDF de la factura: " + ex.getMessage());
        }
    }

    private void agregarEncabezado(Document documento, Factura factura) throws DocumentException {
        PdfPTable encabezado = new PdfPTable(new float[]{60, 40});
        encabezado.setWidthPercentage(100);

        PdfPCell empresa = celdaSinBorde();
        empresa.addElement(new Paragraph(empresaNombre, F_EMPRESA));
        empresa.addElement(new Paragraph("RUC: " + empresaRuc, F_NORMAL));
        empresa.addElement(new Paragraph("Asunción, Paraguay", F_GRIS));
        encabezado.addCell(empresa);

        PdfPCell titulo = celdaSinBorde();
        titulo.setHorizontalAlignment(Element.ALIGN_RIGHT);
        Paragraph factTitulo = new Paragraph("FACTURA", F_TITULO);
        factTitulo.setAlignment(Element.ALIGN_RIGHT);
        titulo.addElement(factTitulo);
        Paragraph tipoNumero = new Paragraph(factura.getTipo() + " N.º " + factura.getNumero(), F_NEGRITA);
        tipoNumero.setAlignment(Element.ALIGN_RIGHT);
        titulo.addElement(tipoNumero);
        Paragraph estado = new Paragraph("Estado: " + factura.getEstado(), F_GRIS);
        estado.setAlignment(Element.ALIGN_RIGHT);
        titulo.addElement(estado);
        encabezado.addCell(titulo);

        documento.add(encabezado);
        documento.add(lineaSeparadora());
    }

    private void agregarDatos(Document documento, Factura factura) throws DocumentException {
        Usuario cliente = factura.getPedido().getUsuario();

        PdfPTable datos = new PdfPTable(new float[]{50, 50});
        datos.setWidthPercentage(100);
        datos.setSpacingBefore(12);
        datos.setSpacingAfter(12);

        PdfPCell izquierda = celdaSinBorde();
        izquierda.addElement(new Paragraph("Cliente", F_GRIS));
        izquierda.addElement(new Paragraph(cliente.getNombre() + " " + cliente.getApellido(), F_NEGRITA));
        izquierda.addElement(new Paragraph(cliente.getEmail(), F_NORMAL));
        datos.addCell(izquierda);

        PdfPCell derecha = celdaSinBorde();
        derecha.addElement(new Paragraph("Fecha: " + factura.getFecha().format(FORMATO_FECHA), F_NORMAL));
        derecha.addElement(new Paragraph("Pedido asociado: N.º " + factura.getPedido().getNumero(), F_NORMAL));
        derecha.addElement(new Paragraph("Atendido por: " + factura.getUsuario().getNombre()
                + " " + factura.getUsuario().getApellido(), F_NORMAL));
        datos.addCell(derecha);

        documento.add(datos);
    }

    private void agregarItems(Document documento, Factura factura) throws DocumentException {
        PdfPTable tabla = new PdfPTable(new float[]{44, 10, 16, 10, 20});
        tabla.setWidthPercentage(100);

        for (String titulo : new String[]{"Producto", "Cant.", "Precio unit.", "IVA", "Subtotal"}) {
            PdfPCell celda = new PdfPCell(new Phrase(titulo, F_ENCABEZADO_TABLA));
            celda.setBackgroundColor(AZUL_OSCURO);
            celda.setPadding(7);
            celda.setBorderColor(GRIS_BORDE);
            tabla.addCell(celda);
        }

        boolean sombreada = false;
        for (PedidoDetalle detalle : factura.getPedido().getDetalles()) {
            Color fondo = sombreada ? GRIS_CLARO : Color.WHITE;
            tabla.addCell(celdaItem(detalle.getProducto().getNombre(), Element.ALIGN_LEFT, fondo));
            tabla.addCell(celdaItem(String.valueOf(detalle.getCantidad()), Element.ALIGN_CENTER, fondo));
            tabla.addCell(celdaItem(gs(detalle.getPrecioUnitario()), Element.ALIGN_RIGHT, fondo));
            tabla.addCell(celdaItem(detalle.getIvaAplicado().stripTrailingZeros().toPlainString() + "%",
                    Element.ALIGN_CENTER, fondo));
            tabla.addCell(celdaItem(gs(detalle.getSubtotal()), Element.ALIGN_RIGHT, fondo));
            sombreada = !sombreada;
        }

        documento.add(tabla);
    }

    private void agregarTotales(Document documento, Factura factura) throws DocumentException {
        PdfPTable totales = new PdfPTable(new float[]{60, 22, 18});
        totales.setWidthPercentage(100);
        totales.setSpacingBefore(10);

        agregarFilaTotal(totales, "Subtotal (sin IVA)", gs(factura.getSubtotal()), F_NORMAL);
        agregarFilaTotal(totales, "IVA incluido", gs(factura.getIva()), F_NORMAL);
        agregarFilaTotal(totales, "TOTAL", gs(factura.getTotal()), F_TOTAL);

        documento.add(totales);
    }

    private void agregarFilaTotal(PdfPTable tabla, String etiqueta, String valor, Font fuente) {
        tabla.addCell(celdaSinBorde());
        PdfPCell celdaEtiqueta = new PdfPCell(new Phrase(etiqueta, fuente));
        celdaEtiqueta.setBorder(Rectangle.NO_BORDER);
        celdaEtiqueta.setHorizontalAlignment(Element.ALIGN_RIGHT);
        celdaEtiqueta.setPadding(4);
        tabla.addCell(celdaEtiqueta);
        PdfPCell celdaValor = new PdfPCell(new Phrase(valor, fuente));
        celdaValor.setBorder(Rectangle.NO_BORDER);
        celdaValor.setHorizontalAlignment(Element.ALIGN_RIGHT);
        celdaValor.setPadding(4);
        tabla.addCell(celdaValor);
    }

    private void agregarPie(Document documento) throws DocumentException {
        documento.add(lineaSeparadora());
        Paragraph pie = new Paragraph(
                "Precios expresados en guaraníes (Gs.) con IVA incluido. "
                        + "Documento generado por TechStore.", F_GRIS);
        pie.setSpacingBefore(8);
        documento.add(pie);
    }

    private PdfPCell celdaSinBorde() {
        PdfPCell celda = new PdfPCell();
        celda.setBorder(Rectangle.NO_BORDER);
        return celda;
    }

    private PdfPCell celdaItem(String texto, int alineacion, Color fondo) {
        PdfPCell celda = new PdfPCell(new Phrase(texto, F_NORMAL));
        celda.setHorizontalAlignment(alineacion);
        celda.setPadding(6);
        celda.setBackgroundColor(fondo);
        celda.setBorderColor(GRIS_BORDE);
        return celda;
    }

    private Element lineaSeparadora() {
        LineSeparator linea = new LineSeparator();
        linea.setLineColor(GRIS_BORDE);
        return new Chunk(linea);
    }

    private String gs(BigDecimal valor) {
        return "Gs. " + formatoNumero.format(valor);
    }
}
