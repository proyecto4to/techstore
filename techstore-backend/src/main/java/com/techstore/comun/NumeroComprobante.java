package com.techstore.comun;

/**
 * El número de un comprobante fiscal paraguayo: {@code ###-###-#######}, o sea
 * establecimiento, punto de expedición y correlativo de siete dígitos.
 *
 * <p>Es el formato que exige el Registro de Comprobantes de la DNIT y el que ya
 * usan las facturas y notas de crédito que emite el sistema. Existe acá porque
 * también hay que aceptarlo tipeado a mano: el número de la factura que mandó
 * un proveedor lo carga una persona mirando un papel, y ahí aparecen
 * {@code 1-1-250} y {@code 001-001-250} tanto como el formato completo.
 */
public final class NumeroComprobante {

    private static final String FORMATO = "\\d{3}-\\d{3}-\\d{7}";

    private NumeroComprobante() {
    }

    public static boolean esValido(String numero) {
        return numero != null && numero.matches(FORMATO);
    }

    /**
     * Completa con ceros a la izquierda cada una de las tres partes, de modo que
     * {@code 1-1-250} quede {@code 001-001-0000250}. Devuelve el texto tal cual
     * si no tiene las tres partes: que lo rechace la validación, con un mensaje
     * que diga qué se esperaba, en vez de deformarlo acá.
     */
    public static String normalizar(String numero) {
        if (numero == null || numero.isBlank()) {
            return null;
        }
        String[] partes = numero.trim().split("-");
        if (partes.length != 3) {
            return numero.trim();
        }
        for (String parte : partes) {
            if (!parte.matches("\\d{1,7}")) {
                return numero.trim();
            }
        }
        return "%03d-%03d-%07d".formatted(
                Integer.parseInt(partes[0]), Integer.parseInt(partes[1]), Integer.parseInt(partes[2]));
    }
}
