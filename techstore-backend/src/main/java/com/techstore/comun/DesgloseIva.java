package com.techstore.comun;

import java.math.BigDecimal;
import java.util.Collection;

/**
 * Un importe repartido en las tres canastas del IVA paraguayo: gravado al 10%,
 * gravado al 5% y exento. Todos con el impuesto incluido, que es como se
 * manejan los precios en el sistema y como los pide la DNIT.
 *
 * <p>Es el desglose que imprime la factura, el que imprime el KuDE y el que
 * asienta el libro de IVA. Los tres lo calculaban por su cuenta.
 */
public record DesgloseIva(Dinero gravado10, Dinero gravado5, Dinero exento) {

    public static final BigDecimal TASA_10 = BigDecimal.TEN;
    public static final BigDecimal TASA_5 = BigDecimal.valueOf(5);

    public static DesgloseIva cero(Moneda moneda) {
        return new DesgloseIva(Dinero.cero(moneda), Dinero.cero(moneda), Dinero.cero(moneda));
    }

    /** Reparte las líneas según la tasa de cada una. */
    public static DesgloseIva de(Moneda moneda, Collection<? extends LineaGravada> lineas) {
        DesgloseIva desglose = cero(moneda);
        for (LineaGravada linea : lineas) {
            desglose = desglose.mas(linea.getIvaAplicado(), linea.getSubtotal());
        }
        return desglose;
    }

    /**
     * Suma un importe a la canasta que le toca. Cualquier tasa que no sea 10 ni
     * 5 cuenta como exenta: es lo que corresponde y evita que una tasa mal
     * cargada desaparezca del total sin que nadie se entere.
     */
    public DesgloseIva mas(BigDecimal tasa, Dinero importe) {
        int puntos = tasa == null ? 0 : tasa.intValue();
        return switch (puntos) {
            case 10 -> new DesgloseIva(gravado10.mas(importe), gravado5, exento);
            case 5 -> new DesgloseIva(gravado10, gravado5.mas(importe), exento);
            default -> new DesgloseIva(gravado10, gravado5, exento.mas(importe));
        };
    }

    /** IVA contenido en lo gravado al 10%. */
    public Dinero iva10() {
        return gravado10.ivaIncluido(TASA_10);
    }

    public Dinero iva5() {
        return gravado5.ivaIncluido(TASA_5);
    }

    public Dinero iva() {
        return iva10().mas(iva5());
    }

    /** La suma de las tres canastas, que tiene que dar el total del documento. */
    public Dinero total() {
        return gravado10.mas(gravado5).mas(exento);
    }
}
