package com.techstore.usuarios;

import com.techstore.comun.ExcepcionTraducible;

/** Se superó el límite de intentos fallidos de login para un email (fuerza bruta). */
public class TooManyAttemptsException extends ExcepcionTraducible {

    public TooManyAttemptsException(String clave, Object... argumentos) {
        super(clave, argumentos);
    }
}
