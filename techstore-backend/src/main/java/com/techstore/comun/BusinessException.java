package com.techstore.comun;

/**
 * Una regla de negocio que el usuario violó y puede corregir: stock que no
 * alcanza, un pedido en el estado equivocado, un cobro que no cierra.
 */
public class BusinessException extends ExcepcionTraducible {

    public BusinessException(String clave, Object... argumentos) {
        super(clave, argumentos);
    }
}
