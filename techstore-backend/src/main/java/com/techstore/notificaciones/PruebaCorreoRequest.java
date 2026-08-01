package com.techstore.notificaciones;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Destinatario para el correo de prueba. */
public record PruebaCorreoRequest(
        @NotBlank @Email(message = "Correo de destino inválido") String destinatario) {
}
