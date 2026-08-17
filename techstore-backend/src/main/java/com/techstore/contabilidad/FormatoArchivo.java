package com.techstore.contabilidad;

/**
 * Los dos formatos que acepta el Registro de Comprobantes de la DNIT.
 *
 * <p>La especificación dice "CSV (delimitado por comas)", pero el ejemplo que
 * trae el mismo documento está separado con punto y coma —que es lo que escribe
 * Excel en configuración regional paraguaya cuando uno elige "CSV delimitado
 * por comas"—. Ante esa contradicción el CSV de acá usa punto y coma, que es lo
 * que muestra el ejemplo oficial.
 *
 * <p>Por eso el formato por defecto es el TXT: el tabulador no aparece dentro
 * de una razón social, así que no hay separador que escapar ni ambigüedad que
 * resolver.
 */
public enum FormatoArchivo {

    /** Delimitado por tabulaciones. El que menos puede salir mal. */
    TXT("txt", "\t"),
    /** Delimitado por punto y coma, como el ejemplo de la DNIT. */
    CSV("csv", ";");

    private final String extension;
    private final String separador;

    FormatoArchivo(String extension, String separador) {
        this.extension = extension;
        this.separador = separador;
    }

    public String extension() {
        return extension;
    }

    public String separador() {
        return separador;
    }
}
