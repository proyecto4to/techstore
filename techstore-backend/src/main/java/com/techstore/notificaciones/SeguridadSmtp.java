package com.techstore.notificaciones;

/** Cómo se cifra la conexión con el servidor de correo. */
public enum SeguridadSmtp {
    /** STARTTLS sobre el puerto de texto plano. Lo habitual en el 587. */
    STARTTLS,
    /** TLS desde el primer byte. Lo habitual en el 465. */
    SSL,
    /**
     * Sin cifrar. Solo tiene sentido contra un servidor de la propia red; la
     * contraseña viaja legible, así que la pantalla lo advierte.
     */
    NINGUNA;

    public static SeguridadSmtp desde(String valor, SeguridadSmtp porDefecto) {
        if (valor == null || valor.isBlank()) {
            return porDefecto;
        }
        try {
            return valueOf(valor.trim().toUpperCase());
        } catch (IllegalArgumentException desconocida) {
            return porDefecto;
        }
    }
}
