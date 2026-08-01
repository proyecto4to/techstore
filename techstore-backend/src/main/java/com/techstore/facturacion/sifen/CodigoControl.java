package com.techstore.facturacion.sifen;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Genera el Código de Control (CDC) de 44 dígitos de un Documento
 * Electrónico del SIFEN (Manual Técnico v150, sección 10).
 *
 * <p>Estructura de los 43 dígitos + 1 dígito verificador:
 * tipo de documento (2) · RUC emisor (8) · DV del RUC (1) · establecimiento (3)
 * · punto de expedición (3) · número de documento (7) · tipo de contribuyente (1)
 * · fecha de emisión AAAAMMDD (8) · tipo de emisión (1) · código de seguridad (9)
 * · DV del CDC (1).
 */
public final class CodigoControl {

    private static final DateTimeFormatter FECHA_CDC = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final SecureRandom RANDOM = new SecureRandom();

    private CodigoControl() {
    }

    /**
     * Dígito verificador por módulo 11, algoritmo del SET (base 11): se
     * ponderan los dígitos de derecha a izquierda con pesos cíclicos 2..11.
     */
    public static int digitoVerificadorModulo11(String numeros) {
        int suma = 0;
        int peso = 2;
        for (int i = numeros.length() - 1; i >= 0; i--) {
            char c = numeros.charAt(i);
            if (c < '0' || c > '9') {
                throw new IllegalArgumentException("El código para el DV solo admite dígitos: " + numeros);
            }
            suma += (c - '0') * peso;
            peso = peso == 11 ? 2 : peso + 1;
        }
        int resto = suma % 11;
        return resto > 1 ? 11 - resto : 0;
    }

    /**
     * Código de seguridad (campo dCodSeg): 9 dígitos, aleatorio, no todo ceros.
     */
    public static String generarCodigoSeguridad() {
        int valor = 1 + RANDOM.nextInt(999_999_999);
        return String.format("%09d", valor);
    }

    /**
     * Arma el CDC completo (44 dígitos) para una factura electrónica.
     *
     * @param tipoDocumento    código del tipo de DE (ej. "01" = factura electrónica)
     * @param rucEmisor        RUC del emisor, 8 dígitos (sin el DV)
     * @param dvRucEmisor      dígito verificador del RUC del emisor
     * @param establecimiento  3 dígitos
     * @param puntoExpedicion  3 dígitos
     * @param numeroDocumento  7 dígitos
     * @param tipoContribuyente 1 = persona física, 2 = persona jurídica
     * @param fechaEmision     fecha de emisión del documento
     * @param tipoEmision      1 = normal, 2 = contingencia
     * @param codigoSeguridad  9 dígitos ({@link #generarCodigoSeguridad()})
     */
    public static String generar(
            String tipoDocumento, String rucEmisor, int dvRucEmisor, String establecimiento,
            String puntoExpedicion, String numeroDocumento, int tipoContribuyente,
            LocalDate fechaEmision, int tipoEmision, String codigoSeguridad) {

        String base = pad(tipoDocumento, 2)
                + pad(rucEmisor, 8)
                + dvRucEmisor
                + pad(establecimiento, 3)
                + pad(puntoExpedicion, 3)
                + pad(numeroDocumento, 7)
                + tipoContribuyente
                + fechaEmision.format(FECHA_CDC)
                + tipoEmision
                + pad(codigoSeguridad, 9);

        if (base.length() != 43) {
            throw new IllegalStateException("La base del CDC debe tener 43 dígitos, tiene " + base.length());
        }
        return base + digitoVerificadorModulo11(base);
    }

    /** Formatea el CDC en grupos de 4 para el KuDE (representación gráfica). */
    public static String enGruposDeCuatro(String cdc) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cdc.length(); i++) {
            if (i > 0 && i % 4 == 0) {
                sb.append(' ');
            }
            sb.append(cdc.charAt(i));
        }
        return sb.toString();
    }

    private static String pad(String valor, int longitud) {
        String limpio = valor == null ? "" : valor.trim();
        if (limpio.length() > longitud) {
            throw new IllegalArgumentException(
                    "El valor '%s' excede la longitud %d del campo del CDC".formatted(limpio, longitud));
        }
        return "0".repeat(longitud - limpio.length()) + limpio;
    }
}
