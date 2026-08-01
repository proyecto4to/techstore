package com.techstore.notificaciones;

/**
 * Configuración SMTP editada desde la app. La contraseña solo se actualiza si
 * viene con valor (dejar vacío conserva la guardada).
 */
public record SmtpConfigRequest(
        String host,
        Integer port,
        String username,
        String password,
        String from) {
}
