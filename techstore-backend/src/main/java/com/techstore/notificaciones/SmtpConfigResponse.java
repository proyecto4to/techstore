package com.techstore.notificaciones;

/** Estado del SMTP para el panel (sin exponer la contraseña). */
public record SmtpConfigResponse(
        String host,
        int port,
        String username,
        String from,
        boolean tienePassword,
        boolean configurado) {
}
