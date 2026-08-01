package com.techstore.comun;

import lombok.Getter;

/**
 * Error que el usuario va a leer, y que por eso viaja con la <b>clave</b> de su
 * mensaje y los argumentos, no con el texto ya armado.
 *
 * <p>Quien traduce es el manejador global de errores, que es el único punto
 * donde se sabe en qué idioma responder: adentro de una transacción esa
 * pregunta todavía no tiene respuesta.
 *
 * <p>No la heredan los errores de programación ({@code IllegalStateException},
 * {@code IllegalArgumentException}): esos no los lee un usuario sino quien
 * mantiene el código, y traducirlos solo dificultaría buscarlos.
 */
@Getter
public abstract class ExcepcionTraducible extends RuntimeException {

    /** Clave en {@code i18n/messages.properties}. */
    private final String clave;

    private final transient Object[] argumentos;

    protected ExcepcionTraducible(String clave, Object... argumentos) {
        // El mensaje de la excepción es la clave: es lo que sale en los logs y
        // en los stack traces, y una clave como "ventas.stock.insuficiente"
        // dice qué pasó sin depender del idioma de quien la lea.
        super(clave);
        this.clave = clave;
        this.argumentos = argumentos;
    }
}
