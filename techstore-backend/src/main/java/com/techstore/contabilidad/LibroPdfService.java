package com.techstore.contabilidad;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.techstore.comun.BusinessException;
import com.techstore.empresa.EmpresaConfigService;
import com.techstore.empresa.EmpresaProperties;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Los libros de IVA impresos.
 *
 * <p>La RG 412/2004 no fija columnas: el artículo 6 solo exige que se anoten
 * las transacciones discriminando gravadas, exoneradas y exportación, con el
 * IVA registrado por separado. Así que las columnas son las mismas que declara
 * el Registro de Comprobantes, que es lo que la DNIT va a cruzar contra el
 * libro si alguna vez lo pide.
 *
 * <p>Va apaisado: son catorce columnas y en A4 vertical no entran sin que los
 * números queden partidos.
 */
@Service
public class LibroPdfService {

    private static final Color NEGRO = new Color(20, 20, 20);
    private static final Color AZUL = new Color(15, 102, 230);
    private static final Color GRIS_TEXTO = new Color(90, 90, 90);
    private static final Color GRIS_FONDO = new Color(235, 235, 235);
    private static final Color GRIS_BORDE = new Color(120, 120, 120);
    private static final Color ROJO = new Color(170, 30, 30);
    private static final Color FONDO_TOTAL = new Color(222, 232, 247);

    private static final Font F_TITULO = new Font(Font.HELVETICA, 15, Font.BOLD, AZUL);
    private static final Font F_EMPRESA = new Font(Font.HELVETICA, 11, Font.BOLD, NEGRO);
    private static final Font F_SECCION = new Font(Font.HELVETICA, 10, Font.BOLD, NEGRO);
    private static final Font F_NORMAL = new Font(Font.HELVETICA, 7.5f, Font.NORMAL, NEGRO);
    private static final Font F_NEGRITA = new Font(Font.HELVETICA, 7.5f, Font.BOLD, NEGRO);
    private static final Font F_RESTA = new Font(Font.HELVETICA, 7.5f, Font.NORMAL, ROJO);
    private static final Font F_CHICA = new Font(Font.HELVETICA, 8, Font.NORMAL, GRIS_TEXTO);
    private static final Font F_AVISO = new Font(Font.HELVETICA, 8, Font.NORMAL, ROJO);

    private static final DateTimeFormatter FMT_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FMT_FECHAHORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final EmpresaConfigService empresaConfig;
    private final NumberFormat formato;

    public LibroPdfService(EmpresaConfigService empresaConfig) {
        this.empresaConfig = empresaConfig;
        this.formato = NumberFormat.getNumberInstance(Locale.forLanguageTag("es-PY"));
        this.formato.setMaximumFractionDigits(2);
        this.formato.setMinimumFractionDigits(0);
    }

    public byte[] libro(LibroIvaResponse libro) {
        boolean ventas = LibroIvaService.VENTAS.equals(libro.libro());
        String titulo = "Libro de IVA — " + (ventas ? "Ventas" : "Compras");
        String contraparte = ventas ? "Comprador" : "Proveedor";

        return documento(titulo, periodo(libro.desde(), libro.hasta()), doc -> {
            try {
                PdfPTable tabla = tabla(
                        new float[]{8, 7, 9, 12, 20, 7, 9, 9, 9, 10, 9, 9},
                        "Fecha", "Tipo", "Timbrado", "Número", contraparte, "Cond.",
                        "Gravado 10%", "IVA 10%", "Gravado 5%", "IVA 5%", "Exentas", "Total");
                tabla.setHeaderRows(1);

                if (libro.lineas().isEmpty()) {
                    tabla.addCell(celdaVacia("Sin comprobantes en el período", 12));
                } else {
                    for (LibroIvaResponse.Linea l : libro.lineas()) {
                        Font fuente = l.resta() ? F_RESTA : F_NORMAL;
                        boolean resta = l.resta();
                        tabla.addCell(celda(l.fecha().format(FMT_FECHA), Element.ALIGN_LEFT, fuente));
                        tabla.addCell(celda(String.valueOf(l.tipoComprobante()), Element.ALIGN_CENTER, fuente));
                        tabla.addCell(celda(vacio(l.timbrado()), Element.ALIGN_CENTER, fuente));
                        tabla.addCell(celda(vacio(l.numero()), Element.ALIGN_LEFT, fuente));
                        tabla.addCell(celda(quienEs(l), Element.ALIGN_LEFT, fuente));
                        tabla.addCell(celda(abreviar(l.condicion()), Element.ALIGN_CENTER, fuente));
                        tabla.addCell(celda(firmado(l.gravado10(), resta), Element.ALIGN_RIGHT, fuente));
                        tabla.addCell(celda(firmado(l.iva10(), resta), Element.ALIGN_RIGHT, fuente));
                        tabla.addCell(celda(firmado(l.gravado5(), resta), Element.ALIGN_RIGHT, fuente));
                        tabla.addCell(celda(firmado(l.iva5(), resta), Element.ALIGN_RIGHT, fuente));
                        tabla.addCell(celda(firmado(l.exento(), resta), Element.ALIGN_RIGHT, fuente));
                        tabla.addCell(celda(firmado(l.total(), resta), Element.ALIGN_RIGHT, fuente));
                    }
                    agregarTotales(tabla, libro.totales());
                }
                doc.add(tabla);

                LibroIvaResponse.Totales t = libro.totales();
                Paragraph resumen = new Paragraph(
                        "%d comprobantes   —   IVA del período: %s %s"
                                .formatted(t.comprobantes(), libro.moneda(), gs(t.iva())),
                        F_SECCION);
                resumen.setSpacingBefore(10);
                doc.add(resumen);

                agregarAvisos(doc, libro.avisos());
            } catch (DocumentException ex) {
                throw new BusinessException("reportes.generacion.fallo", ex.getMessage());
            }
        });
    }

    private void agregarTotales(PdfPTable tabla, LibroIvaResponse.Totales t) {
        tabla.addCell(celdaTotal("TOTALES", Element.ALIGN_LEFT, 6));
        tabla.addCell(celdaTotal(gs(t.gravado10()), Element.ALIGN_RIGHT, 1));
        tabla.addCell(celdaTotal(gs(t.iva10()), Element.ALIGN_RIGHT, 1));
        tabla.addCell(celdaTotal(gs(t.gravado5()), Element.ALIGN_RIGHT, 1));
        tabla.addCell(celdaTotal(gs(t.iva5()), Element.ALIGN_RIGHT, 1));
        tabla.addCell(celdaTotal(gs(t.exento()), Element.ALIGN_RIGHT, 1));
        tabla.addCell(celdaTotal(gs(t.total()), Element.ALIGN_RIGHT, 1));
    }

    /** La liquidación del período: débito contra crédito. */
    public byte[] resumen(ResumenIvaResponse r) {
        return documento("Liquidación del IVA", periodo(r.desde(), r.hasta()), doc -> {
            try {
                PdfPTable kpis = new PdfPTable(3);
                kpis.setWidthPercentage(100);
                kpis.setSpacingAfter(12);
                kpis.addCell(kpi("Débito fiscal (ventas)", r.moneda() + " " + gs(r.debitoFiscal())));
                kpis.addCell(kpi("Crédito fiscal (compras)", r.moneda() + " " + gs(r.creditoFiscal())));
                kpis.addCell(kpi(r.situacion(), r.moneda() + " " + gs(r.saldo().abs())));
                doc.add(kpis);

                doc.add(seccion("Detalle por libro"));
                PdfPTable t = tabla(new float[]{22, 13, 13, 13, 13, 13, 13},
                        "Libro", "Comprob.", "Gravado 10%", "IVA 10%", "Gravado 5%", "IVA 5%", "Exentas");
                agregarFilaLibro(t, "Ventas", r.ventas());
                agregarFilaLibro(t, "Compras", r.compras());
                doc.add(t);

                agregarAvisos(doc, r.avisos());
            } catch (DocumentException ex) {
                throw new BusinessException("reportes.generacion.fallo", ex.getMessage());
            }
        });
    }

    private void agregarFilaLibro(PdfPTable t, String nombre, LibroIvaResponse.Totales tot) {
        t.addCell(celda(nombre, Element.ALIGN_LEFT, F_NEGRITA));
        t.addCell(celda(String.valueOf(tot.comprobantes()), Element.ALIGN_CENTER, F_NORMAL));
        t.addCell(celda(gs(tot.gravado10()), Element.ALIGN_RIGHT, F_NORMAL));
        t.addCell(celda(gs(tot.iva10()), Element.ALIGN_RIGHT, F_NORMAL));
        t.addCell(celda(gs(tot.gravado5()), Element.ALIGN_RIGHT, F_NORMAL));
        t.addCell(celda(gs(tot.iva5()), Element.ALIGN_RIGHT, F_NORMAL));
        t.addCell(celda(gs(tot.exento()), Element.ALIGN_RIGHT, F_NORMAL));
    }

    /**
     * Lo que impide declarar el período. Va impreso en el libro a propósito: si
     * alguien archiva el PDF sin haber mirado la pantalla, el papel dice qué
     * falta en vez de aparentar estar completo.
     *
     */
    private void agregarAvisos(Document doc, List<String> avisos) throws DocumentException {
        if (avisos.isEmpty()) {
            return;
        }
        doc.add(seccion("Pendientes para poder declarar el período"));
        for (String aviso : avisos) {
            Paragraph p = new Paragraph("•  " + aviso, F_AVISO);
            p.setSpacingAfter(1);
            doc.add(p);
        }
    }

    private static String quienEs(LibroIvaResponse.Linea l) {
        String id = vacio(l.identificacion());
        String nombre = l.nombre() == null || l.nombre().isBlank() ? "Consumidor final" : l.nombre();
        return id + "  " + nombre;
    }

    private static String abreviar(String condicion) {
        return "Contado".equals(condicion) ? "Cont." : "Créd.";
    }

    private static String vacio(String valor) {
        return valor == null || valor.isBlank() ? "—" : valor;
    }

    private EmpresaProperties empresa() {
        return empresaConfig.actual();
    }

    private byte[] documento(String titulo, String subtitulo, Consumer<Document> cuerpo) {
        try (ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            // Apaisado: el libro tiene catorce columnas y en vertical no entran.
            Document doc = new Document(PageSize.A4.rotate(), 28, 28, 32, 32);
            PdfWriter.getInstance(doc, salida);
            doc.open();

            doc.add(new Paragraph(empresa().nombre(), F_EMPRESA));
            doc.add(new Paragraph("RUC: " + empresa().ruc(), F_CHICA));
            Paragraph tit = new Paragraph(titulo, F_TITULO);
            tit.setSpacingBefore(6);
            doc.add(tit);
            doc.add(new Paragraph(subtitulo, F_CHICA));
            doc.add(new Paragraph(" ", F_CHICA));

            cuerpo.accept(doc);

            doc.close();
            return salida.toByteArray();
        } catch (DocumentException | java.io.IOException ex) {
            throw new BusinessException("documento.pdf.fallo", ex.getMessage());
        }
    }

    private Paragraph seccion(String texto) {
        Paragraph p = new Paragraph(texto, F_SECCION);
        p.setSpacingBefore(12);
        p.setSpacingAfter(4);
        return p;
    }

    private PdfPTable tabla(float[] anchos, String... titulos) {
        PdfPTable t = new PdfPTable(anchos);
        t.setWidthPercentage(100);
        for (String titulo : titulos) {
            PdfPCell c = new PdfPCell(new Phrase(titulo, F_NEGRITA));
            c.setBackgroundColor(GRIS_FONDO);
            c.setHorizontalAlignment(Element.ALIGN_CENTER);
            c.setPadding(4);
            c.setBorderColor(GRIS_BORDE);
            t.addCell(c);
        }
        return t;
    }

    private PdfPCell celda(String texto, int alineacion, Font fuente) {
        PdfPCell c = new PdfPCell(new Phrase(texto, fuente));
        c.setHorizontalAlignment(alineacion);
        c.setPadding(3);
        c.setBorderColor(GRIS_BORDE);
        return c;
    }

    private PdfPCell celdaTotal(String texto, int alineacion, int colspan) {
        PdfPCell c = new PdfPCell(new Phrase(texto, F_NEGRITA));
        c.setHorizontalAlignment(alineacion);
        c.setColspan(colspan);
        c.setPadding(4);
        c.setBackgroundColor(FONDO_TOTAL);
        c.setBorderColor(GRIS_BORDE);
        return c;
    }

    private PdfPCell celdaVacia(String texto, int colspan) {
        PdfPCell c = new PdfPCell(new Phrase(texto, F_CHICA));
        c.setColspan(colspan);
        c.setPadding(8);
        c.setHorizontalAlignment(Element.ALIGN_CENTER);
        c.setBorderColor(GRIS_BORDE);
        return c;
    }

    private PdfPCell kpi(String etiqueta, String valor) {
        PdfPCell c = new PdfPCell();
        c.setPadding(10);
        c.setBorderColor(GRIS_BORDE);
        Paragraph e = new Paragraph(etiqueta, F_CHICA);
        Paragraph v = new Paragraph(valor, F_SECCION);
        v.setSpacingBefore(2);
        c.addElement(e);
        c.addElement(v);
        return c;
    }

    private String periodo(LocalDate desde, LocalDate hasta) {
        return "Período: " + desde.format(FMT_FECHA) + " al " + hasta.format(FMT_FECHA)
                + "   —   Generado: " + LocalDateTime.now().format(FMT_FECHAHORA);
    }

    private String gs(BigDecimal valor) {
        return formato.format(valor == null ? BigDecimal.ZERO : valor);
    }

    /**
     * El importe de una línea que resta, con su signo. Una nota de crédito se
     * imprime en negativo para que la columna se pueda sumar a ojo y dé el total
     * del pie — pero un cero no lleva signo: "-0" no significa nada.
     */
    private String firmado(BigDecimal valor, boolean resta) {
        if (valor == null || valor.signum() == 0) {
            return gs(BigDecimal.ZERO);
        }
        return (resta ? "-" : "") + gs(valor);
    }
}
