package com.techstore.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Convierte montos a su expresión en letras en español, para el
 * "TOTAL A PAGAR EN LETRAS" de la factura (formato DNIT Paraguay).
 */
public final class NumeroALetras {

    private static final String[] UNIDADES = {
            "", "uno", "dos", "tres", "cuatro", "cinco", "seis", "siete", "ocho", "nueve",
            "diez", "once", "doce", "trece", "catorce", "quince", "dieciséis", "diecisiete",
            "dieciocho", "diecinueve", "veinte", "veintiuno", "veintidós", "veintitrés",
            "veinticuatro", "veinticinco", "veintiséis", "veintisiete", "veintiocho", "veintinueve"
    };

    private static final String[] DECENAS = {
            "", "", "", "treinta", "cuarenta", "cincuenta", "sesenta", "setenta", "ochenta", "noventa"
    };

    private static final String[] CENTENAS = {
            "", "ciento", "doscientos", "trescientos", "cuatrocientos", "quinientos",
            "seiscientos", "setecientos", "ochocientos", "novecientos"
    };

    private NumeroALetras() {
    }

    /**
     * El importe en letras para el documento impreso, nombrando su moneda.
     * Ej.: {@code Gs. 4.700.000} → "guaraníes cuatro millones setecientos mil".
     *
     * <p>La parte fraccionaria solo aparece si la moneda tiene decimales: el
     * guaraní no los tiene, así que nunca sale un "con 00/100" que no significa
     * nada.
     */
    public static String enLetras(Dinero importe) {
        if (importe.moneda() == Moneda.PYG) {
            return enGuaranies(importe.monto());
        }
        String letras = convertir(importe.monto().longValue());
        String resultado = importe.moneda().nombrePlural().toLowerCase() + " " + letras;
        int centimos = importe.monto().remainder(BigDecimal.ONE)
                .movePointRight(importe.moneda().decimales()).abs().intValue();
        if (centimos > 0) {
            resultado += " con " + String.format("%02d", centimos) + "/100";
        }
        return resultado;
    }

    /**
     * Ej.: 4.700.000 → "guaraníes cuatro millones setecientos mil".
     * Si el monto tiene céntimos, se agregan como fracción: "... con 27/100".
     */
    public static String enGuaranies(BigDecimal monto) {
        BigDecimal redondeado = monto.setScale(2, RoundingMode.HALF_UP);
        long entero = redondeado.longValue();
        int centimos = redondeado.remainder(BigDecimal.ONE)
                .movePointRight(2).abs().intValue();

        String letras = convertir(entero);
        String resultado = "guaraníes " + letras;
        if (centimos > 0) {
            resultado += " con " + String.format("%02d", centimos) + "/100";
        }
        return resultado;
    }

    public static String convertir(long numero) {
        if (numero == 0) {
            return "cero";
        }
        if (numero < 0) {
            return "menos " + convertir(-numero);
        }

        StringBuilder resultado = new StringBuilder();

        long millones = numero / 1_000_000;
        long restoMillones = numero % 1_000_000;
        if (millones > 0) {
            if (millones == 1) {
                resultado.append("un millón");
            } else {
                resultado.append(apocopar(convertir(millones))).append(" millones");
            }
        }

        long miles = restoMillones / 1_000;
        long restoMiles = restoMillones % 1_000;
        if (miles > 0) {
            if (!resultado.isEmpty()) {
                resultado.append(' ');
            }
            if (miles == 1) {
                resultado.append("mil");
            } else {
                resultado.append(apocopar(tresCifras((int) miles))).append(" mil");
            }
        }

        if (restoMiles > 0) {
            if (!resultado.isEmpty()) {
                resultado.append(' ');
            }
            resultado.append(tresCifras((int) restoMiles));
        }

        return resultado.toString();
    }

    private static String tresCifras(int n) {
        if (n == 100) {
            return "cien";
        }
        StringBuilder resultado = new StringBuilder();
        int centena = n / 100;
        int resto = n % 100;
        if (centena > 0) {
            resultado.append(CENTENAS[centena]);
            if (resto > 0) {
                resultado.append(' ');
            }
        }
        if (resto > 0) {
            if (resto < 30) {
                resultado.append(UNIDADES[resto]);
            } else {
                int decena = resto / 10;
                int unidad = resto % 10;
                resultado.append(DECENAS[decena]);
                if (unidad > 0) {
                    resultado.append(" y ").append(UNIDADES[unidad]);
                }
            }
        }
        return resultado.toString();
    }

    /** "uno" → "un", "veintiuno" → "veintiún" (antes de "mil"/"millones"). */
    private static String apocopar(String letras) {
        if (letras.endsWith("veintiuno")) {
            return letras.substring(0, letras.length() - "veintiuno".length()) + "veintiún";
        }
        if (letras.endsWith("uno")) {
            return letras.substring(0, letras.length() - 1);
        }
        return letras;
    }
}
