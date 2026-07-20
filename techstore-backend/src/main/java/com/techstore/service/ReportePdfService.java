package com.techstore.service;

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
import com.techstore.config.EmpresaProperties;
import com.techstore.dto.response.MovimientoCajaResponse;
import com.techstore.dto.response.ReporteVentasResponse;
import com.techstore.dto.response.ResumenCajaResponse;
import com.techstore.dto.response.StockBajoResponse;
import com.techstore.dto.response.TopProductoResponse;
import com.techstore.exception.BusinessException;
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
 * Genera los reportes gerenciales en PDF (ventas, stock bajo y arqueo de caja).
 */
@Service
public class ReportePdfService {

    private static final Color NEGRO = new Color(20, 20, 20);
    private static final Color AZUL = new Color(15, 102, 230);
    private static final Color GRIS_TEXTO = new Color(90, 90, 90);
    private static final Color GRIS_FONDO = new Color(235, 235, 235);
    private static final Color GRIS_BORDE = new Color(120, 120, 120);

    private static final Font F_TITULO = new Font(Font.HELVETICA, 16, Font.BOLD, AZUL);
    private static final Font F_EMPRESA = new Font(Font.HELVETICA, 11, Font.BOLD, NEGRO);
    private static final Font F_SECCION = new Font(Font.HELVETICA, 11, Font.BOLD, NEGRO);
    private static final Font F_NORMAL = new Font(Font.HELVETICA, 9, Font.NORMAL, NEGRO);
    private static final Font F_NEGRITA = new Font(Font.HELVETICA, 9, Font.BOLD, NEGRO);
    private static final Font F_CHICA = new Font(Font.HELVETICA, 8, Font.NORMAL, GRIS_TEXTO);

    private static final DateTimeFormatter FMT_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FMT_FECHAHORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final EmpresaProperties empresa;
    private final NumberFormat formato;

    public ReportePdfService(EmpresaProperties empresa) {
        this.empresa = empresa;
        this.formato = NumberFormat.getNumberInstance(Locale.forLanguageTag("es-PY"));
        this.formato.setMaximumFractionDigits(2);
        this.formato.setMinimumFractionDigits(0);
    }

    public byte[] ventas(ReporteVentasResponse r) {
        return documento("Reporte de ventas", periodo(r.desde(), r.hasta()), doc -> {
            try {
                PdfPTable kpis = new PdfPTable(2);
                kpis.setWidthPercentage(100);
                kpis.setSpacingAfter(10);
                kpis.addCell(kpi("Total facturado", "Gs. " + gs(r.totalFacturado())));
                kpis.addCell(kpi("Facturas emitidas", String.valueOf(r.cantidadFacturas())));
                doc.add(kpis);

                doc.add(seccion("Ventas por día"));
                PdfPTable dias = tabla(new float[]{40, 25, 35}, "Fecha", "Facturas", "Total (Gs.)");
                if (r.ventasPorDia().isEmpty()) {
                    dias.addCell(celdaVacia("Sin facturación en el período", 3));
                } else {
                    for (ReporteVentasResponse.VentaDiaria d : r.ventasPorDia()) {
                        dias.addCell(celda(d.fecha().format(FMT_FECHA), Element.ALIGN_LEFT));
                        dias.addCell(celda(String.valueOf(d.cantidadFacturas()), Element.ALIGN_CENTER));
                        dias.addCell(celda(gs(d.total()), Element.ALIGN_RIGHT));
                    }
                }
                doc.add(dias);

                doc.add(seccion("Pedidos por estado"));
                PdfPTable estados = tabla(new float[]{60, 40}, "Estado", "Cantidad");
                if (r.pedidosPorEstado().isEmpty()) {
                    estados.addCell(celdaVacia("Sin pedidos en el período", 2));
                } else {
                    for (ReporteVentasResponse.EstadoCantidad e : r.pedidosPorEstado()) {
                        estados.addCell(celda(etiquetaEstado(e.estado()), Element.ALIGN_LEFT));
                        estados.addCell(celda(String.valueOf(e.cantidad()), Element.ALIGN_CENTER));
                    }
                }
                doc.add(estados);

                doc.add(seccion("Productos más vendidos"));
                PdfPTable prods = tabla(new float[]{50, 22, 28}, "Producto", "Unidades", "Monto (Gs.)");
                if (r.topProductos().isEmpty()) {
                    prods.addCell(celdaVacia("Sin ventas en el período", 3));
                } else {
                    for (TopProductoResponse p : r.topProductos()) {
                        prods.addCell(celda(p.nombre(), Element.ALIGN_LEFT));
                        prods.addCell(celda(String.valueOf(p.unidadesVendidas()), Element.ALIGN_CENTER));
                        prods.addCell(celda(gs(p.montoVendido()), Element.ALIGN_RIGHT));
                    }
                }
                doc.add(prods);

                doc.add(seccion("Mejores clientes"));
                PdfPTable clientes = tabla(new float[]{50, 22, 28}, "Cliente", "Pedidos", "Monto (Gs.)");
                if (r.topClientes().isEmpty()) {
                    clientes.addCell(celdaVacia("Sin compras en el período", 3));
                } else {
                    for (ReporteVentasResponse.TopCliente c : r.topClientes()) {
                        clientes.addCell(celda(c.nombre() + " (" + c.email() + ")", Element.ALIGN_LEFT));
                        clientes.addCell(celda(String.valueOf(c.pedidos()), Element.ALIGN_CENTER));
                        clientes.addCell(celda(gs(c.monto()), Element.ALIGN_RIGHT));
                    }
                }
                doc.add(clientes);
            } catch (DocumentException ex) {
                throw new BusinessException("Error al generar el reporte: " + ex.getMessage());
            }
        });
    }

    public byte[] stockBajo(List<StockBajoResponse> productos, int umbral) {
        return documento("Productos con stock bajo",
                "Umbral: stock disponible ≤ " + umbral + "   —   Generado: " + LocalDateTime.now().format(FMT_FECHAHORA),
                doc -> {
                    try {
                        PdfPTable t = tabla(new float[]{38, 20, 14, 14, 14},
                                "Producto", "Marca", "Stock", "Compr.", "Disp.");
                        if (productos.isEmpty()) {
                            t.addCell(celdaVacia("Ningún producto con stock bajo 👌", 5));
                        } else {
                            for (StockBajoResponse p : productos) {
                                t.addCell(celda(p.nombre(), Element.ALIGN_LEFT));
                                t.addCell(celda(p.marca() == null ? "—" : p.marca(), Element.ALIGN_LEFT));
                                t.addCell(celda(String.valueOf(p.stockReal()), Element.ALIGN_CENTER));
                                t.addCell(celda(String.valueOf(p.stockComprometido()), Element.ALIGN_CENTER));
                                t.addCell(celda(String.valueOf(p.stockDisponible()), Element.ALIGN_CENTER));
                            }
                        }
                        doc.add(t);
                    } catch (DocumentException ex) {
                        throw new BusinessException("Error al generar el reporte: " + ex.getMessage());
                    }
                });
    }

    public byte[] caja(ResumenCajaResponse resumen, List<MovimientoCajaResponse> movimientos) {
        return documento("Arqueo de caja", periodo(resumen.desde(), resumen.hasta()), doc -> {
            try {
                PdfPTable kpis = new PdfPTable(3);
                kpis.setWidthPercentage(100);
                kpis.setSpacingAfter(10);
                kpis.addCell(kpi("Ingresos", "Gs. " + gs(resumen.ingresos())));
                kpis.addCell(kpi("Egresos", "Gs. " + gs(resumen.egresos())));
                kpis.addCell(kpi("Saldo", "Gs. " + gs(resumen.saldo())));
                doc.add(kpis);

                doc.add(seccion("Movimientos"));
                PdfPTable t = tabla(new float[]{10, 20, 14, 34, 22},
                        "N.º", "Fecha", "Tipo", "Concepto", "Monto (Gs.)");
                if (movimientos.isEmpty()) {
                    t.addCell(celdaVacia("Sin movimientos en el período", 5));
                } else {
                    for (MovimientoCajaResponse m : movimientos) {
                        t.addCell(celda(String.valueOf(m.numero()), Element.ALIGN_CENTER));
                        t.addCell(celda(m.fecha().format(FMT_FECHAHORA), Element.ALIGN_LEFT));
                        t.addCell(celda(m.tipo(), Element.ALIGN_CENTER));
                        t.addCell(celda(m.concepto(), Element.ALIGN_LEFT));
                        boolean ingreso = "Ingreso".equals(m.tipo());
                        t.addCell(celda((ingreso ? "+ " : "- ") + gs(m.monto()), Element.ALIGN_RIGHT));
                    }
                }
                doc.add(t);
            } catch (DocumentException ex) {
                throw new BusinessException("Error al generar el reporte: " + ex.getMessage());
            }
        });
    }

    private byte[] documento(String titulo, String subtitulo, Consumer<Document> cuerpo) {
        try (ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 40, 40, 40, 40);
            PdfWriter.getInstance(doc, salida);
            doc.open();

            doc.add(new Paragraph(empresa.nombre(), F_EMPRESA));
            doc.add(new Paragraph("RUC: " + empresa.ruc(), F_CHICA));
            Paragraph tit = new Paragraph(titulo, F_TITULO);
            tit.setSpacingBefore(8);
            doc.add(tit);
            doc.add(new Paragraph(subtitulo, F_CHICA));
            doc.add(new Paragraph(" ", F_CHICA));

            cuerpo.accept(doc);

            doc.close();
            return salida.toByteArray();
        } catch (DocumentException | java.io.IOException ex) {
            throw new BusinessException("No se pudo generar el PDF: " + ex.getMessage());
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
            c.setPadding(5);
            c.setBorderColor(GRIS_BORDE);
            t.addCell(c);
        }
        return t;
    }

    private PdfPCell celda(String texto, int alineacion) {
        PdfPCell c = new PdfPCell(new Phrase(texto, F_NORMAL));
        c.setHorizontalAlignment(alineacion);
        c.setPadding(5);
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
        boolean historial = desde != null && desde.getYear() <= 2000;
        return (historial ? "Período: todo el historial" : "Período: " + desde.format(FMT_FECHA) + " al " + hasta.format(FMT_FECHA))
                + "   —   Generado: " + LocalDateTime.now().format(FMT_FECHAHORA);
    }

    private String gs(BigDecimal valor) {
        return formato.format(valor == null ? BigDecimal.ZERO : valor);
    }

    private static String etiquetaEstado(String codigo) {
        return switch (codigo) {
            case "P" -> "Pendiente";
            case "C" -> "Confirmado";
            case "G" -> "Pagado";
            case "N" -> "Entregado";
            case "R" -> "Devuelto";
            case "X" -> "Anulado";
            default -> codigo;
        };
    }
}
