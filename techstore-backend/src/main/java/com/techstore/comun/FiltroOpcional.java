package com.techstore.comun;

/**
 * Normaliza valores centinela que algunos clientes generados envían para
 * parámetros opcionales. GeneXus representa un identificador numérico vacío
 * como cero; en una búsqueda, ese cero debe equivaler a filtro ausente.
 */
public final class FiltroOpcional {

    private FiltroOpcional() {
    }

    public static Long id(Long valor) {
        return valor != null && valor > 0 ? valor : null;
    }
}
