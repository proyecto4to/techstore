package com.techstore.usuarios;

import com.techstore.comun.ExcepcionTraducible;

/**
 * La cuenta existe y la contraseña es correcta, pero fue desactivada o
 * bloqueada por un administrador. Se distingue de las credenciales inválidas
 * para poder mostrarle al usuario un mensaje claro de baneo.
 */
public class CuentaInactivaException extends ExcepcionTraducible {

    public CuentaInactivaException(String clave, Object... argumentos) {
        super(clave, argumentos);
    }
}
