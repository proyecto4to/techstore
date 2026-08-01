package com.techstore.comun;

/**
 * Se pidió algo que no existe.
 *
 * <p>Cada recurso tiene su propia clave ({@code comun.no.encontrado.pedido},
 * {@code comun.no.encontrado.producto}) en vez de armarse pegando un nombre
 * traducido dentro de una frase molde. Sale más largo el properties, pero cada
 * idioma redacta la frase como corresponde en vez de quedar atado al orden y
 * al género del español.
 */
public class ResourceNotFoundException extends ExcepcionTraducible {

    public ResourceNotFoundException(String clave, Object... argumentos) {
        super(clave, argumentos);
    }
}
