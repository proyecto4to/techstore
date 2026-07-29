package com.techstore.util;

/**
 * RUC paraguayo en formato {@code NNNNNNNN-D}, donde D es el dígito verificador
 * en base 11 que usa la SET/DNIT. Se valida antes de facturar porque el RUC y su
 * DV van dentro del Documento Electrónico: un RUC mal tipeado produce una
 * factura electrónica inválida.
 */
public final class RucParaguayo {

    private RucParaguayo() {
    }

    /** Normaliza: saca espacios y puntos, deja {@code NNNNNNNN-D}. */
    public static String normalizar(String ruc) {
        if (ruc == null) {
            return null;
        }
        String limpio = ruc.trim().replace(".", "").replace(" ", "");
        return limpio.isEmpty() ? null : limpio;
    }

    /** {@code true} si tiene el formato correcto y el dígito verificador coincide. */
    public static boolean esValido(String ruc) {
        String limpio = normalizar(ruc);
        // Se permiten letras: los RUC viejos derivados de cédulas antiguas las tienen.
        if (limpio == null || !limpio.matches("[0-9A-Za-z]{1,10}-\\d")) {
            return false;
        }
        return dv(limpio) == calcularDv(numero(limpio));
    }

    /** Parte anterior al guion (sin el dígito verificador). */
    public static String numero(String ruc) {
        String limpio = normalizar(ruc);
        return limpio.substring(0, limpio.indexOf('-'));
    }

    /** Dígito verificador (parte posterior al guion). */
    public static int dv(String ruc) {
        String limpio = normalizar(ruc);
        return Integer.parseInt(limpio.substring(limpio.indexOf('-') + 1));
    }

    /**
     * Calcula el dígito verificador en base 11: se pondera cada dígito de
     * derecha a izquierda por 2, 3, 4… (reiniciando en 2 al pasar de 11) y el
     * resto de la suma módulo 11 define el DV.
     */
    public static int calcularDv(String numero) {
        int total = 0;
        int peso = 2;
        for (int i = numero.length() - 1; i >= 0; i--) {
            char c = numero.charAt(i);
            // Las letras (RUC derivados de cédulas antiguas) valen su código ASCII.
            int valor = Character.isDigit(c) ? Character.digit(c, 10) : Character.toUpperCase(c);
            total += valor * peso;
            peso++;
            if (peso > 11) {
                peso = 2;
            }
        }
        int resto = total % 11;
        return resto > 1 ? 11 - resto : 0;
    }
}
