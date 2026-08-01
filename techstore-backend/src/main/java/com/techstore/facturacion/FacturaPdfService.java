package com.techstore.facturacion;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
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
import com.techstore.empresa.EmpresaConfigService;
import com.techstore.empresa.EmpresaProperties;
import com.techstore.usuarios.Usuario;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Genera el PDF de la factura con el formato clásico exigido por la
 * DNIT (Paraguay): timbrado con vigencia, condición de venta, columnas
 * de exentas / gravadas 5% / gravadas 10%, total en letras y
 * liquidación del IVA.
 */
@Service
public class FacturaPdfService {

    private static final Color NEGRO = new Color(20, 20, 20);
    private static final Color GRIS_TEXTO = new Color(90, 90, 90);
    private static final Color GRIS_FONDO = new Color(235, 235, 235);
    private static final Color GRIS_BORDE = new Color(120, 120, 120);

    private static final Font F_EMPRESA = new Font(Font.HELVETICA, 13, Font.BOLD, NEGRO);
    private static final Font F_TITULO = new Font(Font.HELVETICA, 13, Font.BOLD, NEGRO);
    private static final Font F_NUMERO = new Font(Font.HELVETICA, 12, Font.BOLD, NEGRO);
    private static final Font F_NORMAL = new Font(Font.HELVETICA, 9, Font.NORMAL, NEGRO);
    private static final Font F_NEGRITA = new Font(Font.HELVETICA, 9, Font.BOLD, NEGRO);
    private static final Font F_CHICA = new Font(Font.HELVETICA, 8, Font.NORMAL, GRIS_TEXTO);
    private static final Font F_TOTAL = new Font(Font.HELVETICA, 11, Font.BOLD, NEGRO);

    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final BigDecimal TASA_5 = BigDecimal.valueOf(5);
    private static final BigDecimal TASA_10 = BigDecimal.TEN;

    private final EmpresaConfigService empresaConfig;
    private final NumberFormat formatoNumero;

    public FacturaPdfService(EmpresaConfigService empresaConfig) {
        this.empresaConfig = empresaConfig;
        this.formatoNumero = NumberFormat.getNumberInstance(Locale.forLanguageTag("es-PY"));
        this.formatoNumero.setMaximumFractionDigits(2);
        this.formatoNumero.setMinimumFractionDigits(0);
    }

    /** Datos vigentes del emisor (editables desde la app). */
    private EmpresaProperties empresa() {
        return empresaConfig.actual();
    }

    /** Una línea imprimible de la factura o nota de crédito. */
    private record ItemPdf(int cantidad, String nombre, Dinero precioUnitario,
                           Dinero subtotal, int tasaIva) {
    }

    public byte[] generar(Factura factura) {
        List<ItemPdf> items = factura.getPedido().getDetalles().stream()
                .map(d -> new ItemPdf(d.getCantidad(), d.getProducto().getNombre(),
                        d.getPrecioUnitario(), d.getSubtotal(),
                        d.getIvaAplicado() == null ? 0 : d.getIvaAplicado().intValue()))
                .toList();
        return render(factura, "FACTURA", factura.getNumero(), factura.getFecha(),
                null, "TOTAL A PAGAR:", items, factura.getTotal());
    }

    public byte[] generarNotaCredito(NotaCredito nc) {
        List<ItemPdf> items = nc.getDetalles().stream()
                .map(d -> new ItemPdf(d.getCantidad(), d.getProducto().getNombre(),
                        d.getPrecioUnitario(), d.getSubtotal(),
                        d.getIvaAplicado() == null ? 0 : d.getIvaAplicado().intValue()))
                .toList();
        return render(nc.getFactura(), "NOTA DE CRÉDITO", nc.getNumero(), nc.getFecha(),
                "Factura afectada: " + nc.getFactura().getNumero() + "   —   Motivo: " + nc.getMotivo(),
                "TOTAL ACREDITADO:", items, nc.getTotal());
    }

    private byte[] render(Factura factura, String tituloDoc, String numeroDoc,
                          java.time.LocalDateTime fecha, String referencia, String etiquetaTotal,
                          List<ItemPdf> items, Dinero total) {
        try (ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            Document documento = new Document(PageSize.A4, 36, 36, 36, 36);
            PdfWriter.getInstance(documento, salida);
            documento.open();

            agregarEncabezado(documento, factura, tituloDoc, numeroDoc);
            agregarCondicionYFecha(documento, factura, fecha);
            agregarCliente(documento, factura);
            if (referencia != null) {
                PdfPTable fila = new PdfPTable(1);
                fila.setWidthPercentage(100);
                fila.addCell(celdaBordeada(new Phrase(referencia, F_NORMAL)));
                documento.add(fila);
            }
            agregarItems(documento, items, total, etiquetaTotal);
            agregarPie(documento);

            documento.close();
            return salida.toByteArray();
        } catch (DocumentException | java.io.IOException ex) {
            throw new BusinessException("No se pudo generar el PDF: " + ex.getMessage());
        }
    }

    private void agregarEncabezado(Document documento, Factura factura,
                                   String tituloDoc, String numeroDoc) throws DocumentException {
        PdfPTable encabezado = new PdfPTable(new float[]{58, 42});
        encabezado.setWidthPercentage(100);

        PdfPCell datosEmpresa = new PdfPCell();
        datosEmpresa.setBorderColor(GRIS_BORDE);
        datosEmpresa.setPadding(8);
        // Isotipo encima del nombre; si no se pudo cargar, el encabezado sigue igual.
        Image logo = LogoPdf.imagen(30f);
        if (logo != null) {
            Paragraph marca = new Paragraph();
            marca.add(new Chunk(logo, 0, -8));
            marca.setSpacingAfter(2);
            datosEmpresa.addElement(marca);
        }
        datosEmpresa.addElement(new Paragraph(empresa().nombre(), F_EMPRESA));
        datosEmpresa.addElement(new Paragraph(empresa().actividad(), F_CHICA));
        datosEmpresa.addElement(new Paragraph(empresa().direccion(), F_NORMAL));
        datosEmpresa.addElement(new Paragraph(empresa().ciudad(), F_NORMAL));
        datosEmpresa.addElement(new Paragraph("Tel.: " + empresa().telefono(), F_NORMAL));
        encabezado.addCell(datosEmpresa);

        PdfPCell cajaTimbrado = new PdfPCell();
        cajaTimbrado.setBorderColor(GRIS_BORDE);
        cajaTimbrado.setPadding(8);
        cajaTimbrado.setHorizontalAlignment(Element.ALIGN_CENTER);
        Paragraph ruc = new Paragraph("RUC: " + empresa().ruc(), F_NEGRITA);
        ruc.setAlignment(Element.ALIGN_CENTER);
        cajaTimbrado.addElement(ruc);
        Paragraph timbrado = new Paragraph("TIMBRADO N.º " + empresa().timbrado(), F_NEGRITA);
        timbrado.setAlignment(Element.ALIGN_CENTER);
        cajaTimbrado.addElement(timbrado);
        Paragraph vigencia = new Paragraph("Vigencia: " + empresa().timbradoVigencia(), F_CHICA);
        vigencia.setAlignment(Element.ALIGN_CENTER);
        cajaTimbrado.addElement(vigencia);
        Paragraph titulo = new Paragraph(tituloDoc, F_TITULO);
        titulo.setAlignment(Element.ALIGN_CENTER);
        titulo.setSpacingBefore(6);
        cajaTimbrado.addElement(titulo);
        Paragraph numero = new Paragraph(numeroDoc, F_NUMERO);
        numero.setAlignment(Element.ALIGN_CENTER);
        cajaTimbrado.addElement(numero);
        encabezado.addCell(cajaTimbrado);

        documento.add(encabezado);
    }

    private void agregarCondicionYFecha(Document documento, Factura factura,
                                        java.time.LocalDateTime fecha) throws DocumentException {
        boolean contado = "CONTADO".equalsIgnoreCase(factura.getTipo());
        PdfPTable fila = new PdfPTable(new float[]{60, 40});
        fila.setWidthPercentage(100);

        PdfPCell condicion = celdaBordeada(new Phrase(
                "Condición de venta:   CONTADO [" + (contado ? "X" : " ") + "]"
                        + "   CRÉDITO [" + (contado ? " " : "X") + "]", F_NORMAL));
        fila.addCell(condicion);

        PdfPCell celdaFecha = celdaBordeada(new Phrase(
                "Fecha de emisión: " + fecha.format(FORMATO_FECHA), F_NORMAL));
        fila.addCell(celdaFecha);

        documento.add(fila);
    }

    private void agregarCliente(Document documento, Factura factura) throws DocumentException {
        Usuario cliente = factura.getPedido().getUsuario();
        // El receptor tiene que ser el mismo que el de la factura: si se emitió a
        // un RUC, la nota de crédito que la anula sale a ese mismo contribuyente.
        String rucReceptor = factura.getReceptorRuc();
        String nombreReceptor = rucReceptor == null
                ? cliente.getNombre() + " " + cliente.getApellido()
                : factura.getReceptorNombre();

        PdfPTable datos = new PdfPTable(new float[]{60, 40});
        datos.setWidthPercentage(100);
        datos.addCell(celdaBordeada(new Phrase("Nombre o Razón Social: " + nombreReceptor, F_NORMAL)));
        datos.addCell(celdaBordeada(new Phrase(
                "RUC / C.I.: " + (rucReceptor == null ? "Consumidor final" : rucReceptor), F_NORMAL)));
        datos.addCell(celdaBordeada(new Phrase("Email: " + cliente.getEmail(), F_NORMAL)));
        datos.addCell(celdaBordeada(new Phrase(
                "Pedido asociado: N.º " + factura.getPedido().getNumero(), F_NORMAL)));

        documento.add(datos);
    }

    private void agregarItems(Document documento, List<ItemPdf> items, Dinero total,
                              String etiquetaTotal) throws DocumentException {
        PdfPTable tabla = new PdfPTable(new float[]{9, 33, 14, 14, 14, 16});
        tabla.setWidthPercentage(100);
        tabla.setSpacingBefore(8);

        String[] titulos = {"CANT.", "DESCRIPCIÓN", "PRECIO UNIT.", "EXENTAS", "GRAVADAS 5%", "GRAVADAS 10%"};
        for (String tituloColumna : titulos) {
            PdfPCell celda = new PdfPCell(new Phrase(tituloColumna, F_NEGRITA));
            celda.setBackgroundColor(GRIS_FONDO);
            celda.setHorizontalAlignment(Element.ALIGN_CENTER);
            celda.setPadding(6);
            celda.setBorderColor(GRIS_BORDE);
            tabla.addCell(celda);
        }

        Dinero exentas = Dinero.cero(total.moneda());
        Dinero gravadas5 = Dinero.cero(total.moneda());
        Dinero gravadas10 = Dinero.cero(total.moneda());

        for (ItemPdf item : items) {
            int tasa = item.tasaIva();
            Dinero monto = item.subtotal();

            tabla.addCell(celdaItem(String.valueOf(item.cantidad()), Element.ALIGN_CENTER));
            tabla.addCell(celdaItem(item.nombre(), Element.ALIGN_LEFT));
            tabla.addCell(celdaItem(numero(item.precioUnitario()), Element.ALIGN_RIGHT));
            tabla.addCell(celdaItem(tasa == 0 ? numero(monto) : "", Element.ALIGN_RIGHT));
            tabla.addCell(celdaItem(tasa == 5 ? numero(monto) : "", Element.ALIGN_RIGHT));
            tabla.addCell(celdaItem(tasa == 10 ? numero(monto) : "", Element.ALIGN_RIGHT));

            switch (tasa) {
                case 5 -> gravadas5 = gravadas5.mas(monto);
                case 10 -> gravadas10 = gravadas10.mas(monto);
                default -> exentas = exentas.mas(monto);
            }
        }

        PdfPCell subtotales = new PdfPCell(new Phrase("SUB-TOTALES:", F_NEGRITA));
        subtotales.setColspan(3);
        subtotales.setHorizontalAlignment(Element.ALIGN_RIGHT);
        subtotales.setPadding(6);
        subtotales.setBorderColor(GRIS_BORDE);
        subtotales.setBackgroundColor(GRIS_FONDO);
        tabla.addCell(subtotales);
        for (Dinero columna : new Dinero[]{exentas, gravadas5, gravadas10}) {
            PdfPCell celda = new PdfPCell(new Phrase(numero(columna), F_NEGRITA));
            celda.setHorizontalAlignment(Element.ALIGN_RIGHT);
            celda.setPadding(6);
            celda.setBorderColor(GRIS_BORDE);
            celda.setBackgroundColor(GRIS_FONDO);
            tabla.addCell(celda);
        }

        PdfPCell celdaEtiquetaTotal = new PdfPCell(new Phrase(etiquetaTotal, F_TOTAL));
        celdaEtiquetaTotal.setColspan(3);
        celdaEtiquetaTotal.setHorizontalAlignment(Element.ALIGN_RIGHT);
        celdaEtiquetaTotal.setPadding(7);
        celdaEtiquetaTotal.setBorderColor(GRIS_BORDE);
        tabla.addCell(celdaEtiquetaTotal);
        PdfPCell valorTotal = new PdfPCell(
                new Phrase(total.moneda().simbolo() + " " + numero(total), F_TOTAL));
        valorTotal.setColspan(3);
        valorTotal.setHorizontalAlignment(Element.ALIGN_RIGHT);
        valorTotal.setPadding(7);
        valorTotal.setBorderColor(GRIS_BORDE);
        tabla.addCell(valorTotal);

        PdfPCell enLetras = new PdfPCell(new Phrase(
                etiquetaTotal.replace(":", "") + " (EN LETRAS): "
                        + capitalizar(NumeroALetras.enLetras(total)) + ".-", F_NORMAL));
        enLetras.setColspan(6);
        enLetras.setPadding(6);
        enLetras.setBorderColor(GRIS_BORDE);
        tabla.addCell(enLetras);

        // El mismo cálculo que usa el cobro para guardar el IVA de la factura, así
        // el impuesto impreso y el guardado no pueden discrepar.
        Dinero iva5 = gravadas5.ivaIncluido(TASA_5);
        Dinero iva10 = gravadas10.ivaIncluido(TASA_10);
        PdfPCell liquidacion = new PdfPCell(new Phrase(
                "LIQUIDACIÓN DEL IVA:   (5%) " + numero(iva5)
                        + "   (10%) " + numero(iva10)
                        + "   TOTAL IVA: " + numero(iva5.mas(iva10)), F_NORMAL));
        liquidacion.setColspan(6);
        liquidacion.setPadding(6);
        liquidacion.setBorderColor(GRIS_BORDE);
        tabla.addCell(liquidacion);

        documento.add(tabla);
    }

    private void agregarPie(Document documento) throws DocumentException {
        Paragraph pie = new Paragraph("Original: Cliente   —   Duplicado: Archivo Tributario", F_CHICA);
        pie.setAlignment(Element.ALIGN_CENTER);
        pie.setSpacingBefore(10);
        documento.add(pie);
    }

    private PdfPCell celdaBordeada(Phrase contenido) {
        PdfPCell celda = new PdfPCell(contenido);
        celda.setPadding(6);
        celda.setBorderColor(GRIS_BORDE);
        return celda;
    }

    private PdfPCell celdaItem(String texto, int alineacion) {
        PdfPCell celda = new PdfPCell(new Phrase(texto, F_NORMAL));
        celda.setHorizontalAlignment(alineacion);
        celda.setPadding(6);
        celda.setBorderColor(GRIS_BORDE);
        return celda;
    }

    private String numero(Dinero valor) {
        return formatoNumero.format(valor.monto());
    }

    private static String capitalizar(String texto) {
        return texto.isEmpty() ? texto
                : Character.toUpperCase(texto.charAt(0)) + texto.substring(1);
    }
}
