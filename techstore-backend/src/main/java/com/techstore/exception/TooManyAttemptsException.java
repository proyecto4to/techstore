package com.techstore.exception;

/** Se superó el límite de intentos fallidos de login para un email (fuerza bruta). */
public class TooManyAttemptsException extends RuntimeException {

    public TooManyAttemptsException(String mensaje) {
        super(mensaje);
    }
}
