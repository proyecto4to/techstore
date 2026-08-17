package com.techstore.contabilidad;

import com.techstore.comun.Dinero;
import com.techstore.comun.Moneda;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Un libro de IVA armado: los comprobantes del período en orden cronológico y
 * los totales que van al pie.
 *
 * <p>Sirve tanto para la pantalla como para el PDF, igual que hacen los
 * reportes de ventas. Los importes salen como números planos y la moneda va
 * aparte, que es la convención de la API.
 */
public record LibroIvaResponse(
        /** "Ventas" o "Compras". */
        String libro,
        LocalDate desde,
        LocalDate hasta,
        String moneda,
        List<Linea> lineas,
        Totales totales,
        /**
         * Lo que impide declarar el período tal como está: un comprobante sin
         * timbrado, un comprador sin identificar, un documento en otra moneda.
         * Vacío significa que el archivo para la DNIT se puede generar.
         */
        List<String> avisos) {

    public record Linea(
            LocalDate fecha,
            int tipoComprobante,
            String tipoComprobanteEtiqueta,
            String timbrado,
            String numero,
            int tipoIdentificacion,
            String identificacion,
            String nombre,
            String condicion,
            BigDecimal gravado10,
            BigDecimal gravado5,
            BigDecimal exento,
            BigDecimal total,
            BigDecimal iva10,
            BigDecimal iva5,
            /** Si resta en vez de sumar: las notas de crédito. */
            boolean resta,
            String asociadoNumero) {

        static Linea de(LineaLibro l) {
            return new Linea(
                    l.fecha(),
                    l.tipo().codigo(),
                    l.tipo().etiqueta(),
                    l.timbrado(),
                    l.numero(),
                    l.tipoIdentificacion().codigo(),
                    l.identificacion(),
                    l.nombre(),
                    l.condicion().etiqueta(),
                    l.gravado10().monto(),
                    l.gravado5().monto(),
                    l.exento().monto(),
                    l.total().monto(),
                    l.iva10().monto(),
                    l.iva5().monto(),
                    l.resta(),
                    l.asociadoNumero());
        }
    }

    /**
     * El pie del libro. Las notas de crédito ya vienen restadas: es lo que se
     * declara y lo que se liquida.
     */
    public record Totales(
            int comprobantes,
            BigDecimal gravado10,
            BigDecimal gravado5,
            BigDecimal exento,
            BigDecimal total,
            BigDecimal iva10,
            BigDecimal iva5,
            BigDecimal iva) {

        static Totales de(Moneda moneda, List<LineaLibro> lineas) {
            Dinero gravado10 = Dinero.cero(moneda);
            Dinero gravado5 = Dinero.cero(moneda);
            Dinero exento = Dinero.cero(moneda);
            Dinero total = Dinero.cero(moneda);
            Dinero iva10 = Dinero.cero(moneda);
            Dinero iva5 = Dinero.cero(moneda);
            for (LineaLibro linea : lineas) {
                gravado10 = gravado10.mas(linea.aporteGravado10());
                gravado5 = gravado5.mas(linea.aporteGravado5());
                exento = exento.mas(linea.aporteExento());
                total = total.mas(linea.aporteTotal());
                iva10 = iva10.mas(linea.resta() ? linea.iva10().negado() : linea.iva10());
                iva5 = iva5.mas(linea.resta() ? linea.iva5().negado() : linea.iva5());
            }
            return new Totales(lineas.size(), gravado10.monto(), gravado5.monto(), exento.monto(),
                    total.monto(), iva10.monto(), iva5.monto(), iva10.mas(iva5).monto());
        }
    }

    static LibroIvaResponse de(String libro, LocalDate desde, LocalDate hasta,
                               Moneda moneda, List<LineaLibro> lineas, List<String> avisos) {
        return new LibroIvaResponse(
                libro, desde, hasta, moneda.codigo(),
                lineas.stream().map(Linea::de).toList(),
                Totales.de(moneda, lineas),
                List.copyOf(avisos));
    }
}
