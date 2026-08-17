package com.techstore.contabilidad;

import com.techstore.comun.CondicionOperacion;
import com.techstore.comun.Dinero;
import com.techstore.comun.RucParaguayo;
import com.techstore.comun.TipoComprobante;
import com.techstore.comun.TipoIdentificacion;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Un asiento del libro de IVA: un comprobante, con todo lo que hay que saber
 * de él para el libro impreso y para el archivo de la DNIT.
 *
 * <p>Es la misma forma para ventas y para compras. Cambia quién es la
 * contraparte —el comprador o el proveedor— pero las columnas son las mismas, y
 * tenerlas una sola vez evita que el libro de compras y el de ventas calculen
 * el IVA de maneras distintas, que es exactamente lo que pasaba antes de que
 * existiera el tipo {@link Dinero}.
 *
 * <p><b>Los montos van siempre positivos</b>, incluso los de una nota de
 * crédito. Así los pide la especificación: el signo lo lleva el tipo de
 * comprobante, no el importe. Para sumar el período están los métodos
 * {@code aporte*}, que sí aplican el signo.
 */
public record LineaLibro(
        LocalDate fecha,
        TipoComprobante tipo,
        /** Nulo solo donde la DNIT no lo exige, o en documentos viejos sin timbrado registrado. */
        String timbrado,
        String numero,
        TipoIdentificacion tipoIdentificacion,
        /** RUC o documento de la contraparte, como se muestra: con el dígito verificador. */
        String identificacion,
        String nombre,
        CondicionOperacion condicion,
        Dinero gravado10,
        Dinero gravado5,
        Dinero exento,
        Dinero total,
        String asociadoNumero,
        String asociadoTimbrado) {

    public static final BigDecimal TASA_10 = BigDecimal.TEN;
    public static final BigDecimal TASA_5 = BigDecimal.valueOf(5);

    /** IVA contenido en lo gravado al 10%: los precios acá vienen con el impuesto adentro. */
    public Dinero iva10() {
        return gravado10.ivaIncluido(TASA_10);
    }

    public Dinero iva5() {
        return gravado5.ivaIncluido(TASA_5);
    }

    public Dinero iva() {
        return iva10().mas(iva5());
    }

    /** Si este comprobante resta en vez de sumar (nota de crédito). */
    public boolean resta() {
        return tipo.esCorrectivoNegativo();
    }

    public Dinero aporteGravado10() {
        return firmado(gravado10);
    }

    public Dinero aporteGravado5() {
        return firmado(gravado5);
    }

    public Dinero aporteExento() {
        return firmado(exento);
    }

    public Dinero aporteTotal() {
        return firmado(total);
    }

    /** Lo que este comprobante suma o resta al IVA del período. */
    public Dinero aporteIva() {
        return firmado(iva());
    }

    /**
     * La identificación como la quiere el archivo de la DNIT: el RUC va
     * <b>sin dígito verificador</b>. Los demás documentos van tal cual.
     */
    public String identificacionParaDnit() {
        if (identificacion == null) {
            return null;
        }
        if (tipoIdentificacion == TipoIdentificacion.RUC && identificacion.contains("-")) {
            return RucParaguayo.numero(identificacion);
        }
        return identificacion;
    }

    private Dinero firmado(Dinero importe) {
        return resta() ? importe.negado() : importe;
    }
}
