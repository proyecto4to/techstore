package com.techstore.usuarios;

import jakarta.validation.constraints.NotBlank;

/**
 * La contraseña con la que alguien vuelve a probar que es quien dice ser.
 *
 * <p>No lleva el correo a propósito: se reautentica siempre al usuario de la
 * sesión en curso, así este endpoint no sirve para probar contraseñas ajenas.
 */
public record ReautenticacionRequest(@NotBlank String password) {
}
