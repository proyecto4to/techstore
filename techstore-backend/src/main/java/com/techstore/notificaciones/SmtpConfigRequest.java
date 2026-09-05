package com.techstore.notificaciones;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Configuración SMTP editada desde la app.
 *
 * <p>La contraseña del servidor solo se actualiza si viene con valor: dejarla
 * vacía conserva la guardada, porque la pantalla nunca la muestra y no puede
 * devolverla.
 *
 * <p>{@code passwordActual} es la del administrador que está editando, no la
 * del servidor de correo. Sin ella la operación no avanza: quien tenga una
 * sesión abierta y sin dueño delante no puede desviar el correo de la empresa.
 */
public record SmtpConfigRequest(
        @NotBlank(message = "{validacion.smtp.host.obligatorio}") @Size(max = 255) String host,
        @Min(value = 1, message = "{validacion.smtp.puerto.rango}")
        @Max(value = 65535, message = "{validacion.smtp.puerto.rango}")
        Integer port,
        @Size(max = 255) String username,
        @Size(max = 255) String password,
        @Email(message = "{validacion.email.formato}") @Size(max = 255) String from,
        @Size(max = 120) String fromNombre,
        String seguridad,
        @NotBlank(message = "{validacion.password.actual.obligatoria}") String passwordActual) {
}
