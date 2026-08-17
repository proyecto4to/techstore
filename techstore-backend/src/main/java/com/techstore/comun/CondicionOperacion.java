package com.techstore.comun;

/**
 * Si la operación se pagó al momento o quedó a plazo. Es la Tabla 2 del
 * Registro de Comprobantes de la DNIT (RG 90/2021), campo 13 tanto en ventas
 * como en compras.
 *
 * <p>Los nombres son los que guarda PostgreSQL en el tipo {@code
 * condicion_operacion}, así que no llevan tilde: renombrarlos obliga a migrar
 * el tipo.
 */
public enum CondicionOperacion {

    Contado(1, "Contado"),
    Credito(2, "Crédito");

    private final int codigo;
    private final String etiqueta;

    CondicionOperacion(int codigo, String etiqueta) {
        this.codigo = codigo;
        this.etiqueta = etiqueta;
    }

    /** El código que viaja en el archivo de la DNIT. */
    public int codigo() {
        return codigo;
    }

    /** Cómo se lee en pantalla y en los libros impresos. */
    public String etiqueta() {
        return etiqueta;
    }
}
