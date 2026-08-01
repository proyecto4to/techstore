package com.techstore.util;

/**
 * Moneda en la que se expresa un importe, con las dos cosas que cambian la
 * aritmética: cuántos decimales admite y cómo se la nombra en un documento.
 *
 * <p>El número de decimales no es un detalle de presentación. El guaraní no
 * tiene subunidad: un importe con centavos no existe, y redondearlo al
 * imprimirlo —como se hacía— deja el importe guardado distinto del importe
 * impreso. Por eso el redondeo vive acá, en la moneda, y no en cada cálculo.
 *
 * <p>El código ISO 4217 es el propio {@code name()}: es el que el SIFEN espera
 * en {@code cMoneOpe} y el que entiende {@code Intl.NumberFormat} en el panel.
 */
public enum Moneda {

    /** Guaraní paraguayo. Sin centavos: 1.500.000, nunca 1.500.000,50. */
    PYG("Guaraní", "Guaraníes", "Gs.", 0),

    /** Dólar estadounidense. Existe para que la aritmética no asuma una sola moneda. */
    USD("Dólar estadounidense", "Dólares estadounidenses", "US$", 2);

    private final String nombre;
    private final String nombrePlural;
    private final String simbolo;
    private final int decimales;

    Moneda(String nombre, String nombrePlural, String simbolo, int decimales) {
        this.nombre = nombre;
        this.nombrePlural = nombrePlural;
        this.simbolo = simbolo;
        this.decimales = decimales;
    }

    /** Nombre para imprimir en un documento ({@code dDesMoneOpe} del SIFEN). */
    public String nombre() {
        return nombre;
    }

    /**
     * Para los textos que hablan de un importe y no de la moneda en sí: el KuDE
     * titula "TOTAL EN GUARANÍES", no "TOTAL EN GUARANÍ".
     */
    public String nombrePlural() {
        return nombrePlural;
    }

    /** Símbolo corto para las pantallas y los PDF. */
    public String simbolo() {
        return simbolo;
    }

    /** Decimales que admite la moneda: 0 para el guaraní, 2 para el dólar. */
    public int decimales() {
        return decimales;
    }

    /** Código ISO 4217. */
    public String codigo() {
        return name();
    }

    /**
     * La moneda del sistema mientras no haya más de una. Es un único lugar al
     * que apuntar: cuando alguna operación pueda ser en otra moneda, se cambia
     * quién la provee sin salir a buscar {@code PYG} por todo el código.
     */
    public static Moneda porDefecto() {
        return PYG;
    }
}
