package com.techstore.comun;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.Objects;

/**
 * Un importe con su moneda. Inmutable: toda operación devuelve un importe
 * nuevo, nunca modifica el original.
 *
 * <p>Existe para que el dinero deje de ser un {@link BigDecimal} suelto. Un
 * {@code BigDecimal} no sabe en qué moneda está, ni cuántos decimales le
 * corresponden, ni se niega a sumarse con otro de otra moneda. Las tres cosas
 * importan en un sistema que emite facturas con validez fiscal:
 *
 * <ul>
 *   <li><b>El redondeo lo fija la moneda.</b> Antes, el IVA se calculaba a dos
 *       decimales al guardarlo y a cero al imprimirlo, así que la factura
 *       guardada y el KuDE podían no coincidir. Ahora hay un solo redondeo.</li>
 *   <li><b>No se pueden mezclar monedas.</b> Sumar guaraníes con dólares tira
 *       excepción en vez de producir un número sin sentido.</li>
 *   <li><b>El IVA se calcula en un solo lugar.</b> Estaba escrito tres veces,
 *       con redondeos distintos.</li>
 * </ul>
 *
 * <p>El monto se normaliza a los decimales de la moneda al construirse, así que
 * dos importes iguales son siempre {@code equals} entre sí: {@code 1000} y
 * {@code 1000,00} guaraníes son el mismo importe y no dos distintos.
 */
public record Dinero(BigDecimal monto, Moneda moneda) implements Comparable<Dinero> {

    /**
     * Redondeo comercial, el que se espera en una factura: 0,5 sube. Es el mismo
     * que ya usaban los cálculos de IVA y el KuDE.
     */
    public static final RoundingMode REDONDEO = RoundingMode.HALF_UP;

    private static final BigDecimal CIEN = BigDecimal.valueOf(100);

    public Dinero {
        Objects.requireNonNull(moneda, "la moneda es obligatoria");
        Objects.requireNonNull(monto, "el monto es obligatorio");
        monto = monto.setScale(moneda.decimales(), REDONDEO);
    }

    public static Dinero de(BigDecimal monto, Moneda moneda) {
        return new Dinero(monto, moneda);
    }

    public static Dinero de(long monto, Moneda moneda) {
        return new Dinero(BigDecimal.valueOf(monto), moneda);
    }

    public static Dinero cero(Moneda moneda) {
        return new Dinero(BigDecimal.ZERO, moneda);
    }

    /**
     * Igual que {@link #de(BigDecimal, Moneda)} pero acepta {@code null} y lo
     * trata como cero. Para las columnas monetarias que admiten nulo, donde un
     * nulo significa "todavía no hubo movimiento", no un importe desconocido.
     */
    public static Dinero deONulo(BigDecimal monto, Moneda moneda) {
        return monto == null ? cero(moneda) : de(monto, moneda);
    }

    public static Dinero sumar(Moneda moneda, Collection<Dinero> importes) {
        Dinero total = cero(moneda);
        for (Dinero importe : importes) {
            total = total.mas(importe);
        }
        return total;
    }

    public Dinero mas(Dinero otro) {
        exigirMismaMoneda(otro);
        return new Dinero(monto.add(otro.monto), moneda);
    }

    public Dinero menos(Dinero otro) {
        exigirMismaMoneda(otro);
        return new Dinero(monto.subtract(otro.monto), moneda);
    }

    /** Multiplica por una cantidad de unidades: el subtotal de una línea. */
    public Dinero por(int cantidad) {
        return new Dinero(monto.multiply(BigDecimal.valueOf(cantidad)), moneda);
    }

    public Dinero por(BigDecimal factor) {
        return new Dinero(monto.multiply(factor), moneda);
    }

    public Dinero negado() {
        return new Dinero(monto.negate(), moneda);
    }

    public Dinero absoluto() {
        return new Dinero(monto.abs(), moneda);
    }

    /**
     * El IVA contenido en este importe, para una tasa dada en porcentaje
     * ({@code 10} o {@code 5}). Acá los precios se manejan con el IVA
     * <em>incluido</em>, así que el impuesto no se suma: se extrae.
     *
     * <p>{@code iva = monto × tasa ÷ (100 + tasa)} — para el 10% es dividir por
     * 11, y para el 5% por 21, que es exactamente lo que imprime el KuDE.
     *
     * <p>Tasa nula, cero o negativa significa exento: devuelve cero.
     */
    public Dinero ivaIncluido(BigDecimal tasa) {
        if (tasa == null || tasa.signum() <= 0) {
            return cero(moneda);
        }
        return new Dinero(
                monto.multiply(tasa).divide(CIEN.add(tasa), moneda.decimales(), REDONDEO),
                moneda);
    }

    /** Lo que queda de este importe una vez descontado el IVA que trae adentro. */
    public Dinero sinIva(BigDecimal tasa) {
        return menos(ivaIncluido(tasa));
    }

    public boolean esCero() {
        return monto.signum() == 0;
    }

    public boolean esPositivo() {
        return monto.signum() > 0;
    }

    public boolean esNegativo() {
        return monto.signum() < 0;
    }

    public boolean mayorQue(Dinero otro) {
        return compareTo(otro) > 0;
    }

    public boolean menorQue(Dinero otro) {
        return compareTo(otro) < 0;
    }

    @Override
    public int compareTo(Dinero otro) {
        exigirMismaMoneda(otro);
        return monto.compareTo(otro.monto);
    }

    /** El importe sin separadores ni símbolo, para mensajes y para el XML del SIFEN. */
    public String plano() {
        return monto.toPlainString();
    }

    @Override
    public String toString() {
        return moneda.simbolo() + " " + monto.toPlainString();
    }

    private void exigirMismaMoneda(Dinero otro) {
        Objects.requireNonNull(otro, "el otro importe es obligatorio");
        if (moneda != otro.moneda) {
            throw new MonedasIncompatiblesException(moneda, otro.moneda);
        }
    }

    /**
     * Se intentó operar con dos importes de monedas distintas. Es un error de
     * programación, no una regla de negocio que el usuario pueda corregir: por
     * eso no hereda de la excepción de negocio ni se traduce a un 400.
     */
    public static class MonedasIncompatiblesException extends RuntimeException {

        public MonedasIncompatiblesException(Moneda una, Moneda otra) {
            super("No se pueden operar importes en monedas distintas: %s y %s"
                    .formatted(una.codigo(), otra.codigo()));
        }
    }
}
