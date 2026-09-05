package com.techstore.notificaciones;

/**
 * Estado del SMTP para el panel.
 *
 * <p>La contraseña del servidor no viaja nunca, ni siquiera parcial:
 * {@code tienePassword} dice si hay una guardada y la pantalla muestra un valor
 * enmascarado. Devolver aunque sea los últimos caracteres sería filtrarla de a
 * pedazos.
 */
public record SmtpConfigResponse(
        String host,
        int port,
        String username,
        String from,
        String fromNombre,
        String seguridad,
        boolean tienePassword,
        boolean configurado) {
}
