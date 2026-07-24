package com.techstore.dto.response;

/** Resultado del intento de envío del correo de prueba. */
public record PruebaCorreoResponse(boolean exito, String mensaje) {
}
