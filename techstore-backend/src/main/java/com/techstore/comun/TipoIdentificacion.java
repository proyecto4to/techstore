package com.techstore.comun;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Con qué documento se identifica la contraparte de una operación: Tabla 3 del
 * Registro de Comprobantes de la DNIT (RG 90/2021).
 *
 * <p>Importa porque decide dos cosas del archivo que se le presenta a la
 * administración: qué se acepta en el campo del número de identificación, y si
 * hay que informar el nombre. Cuando la contraparte tiene RUC o cédula
 * paraguaya la DNIT ya sabe cómo se llama y no quiere que se lo repitan; para
 * un pasaporte o una identificación tributaria del exterior, sí.
 */
public enum TipoIdentificacion {

    RUC(11, "RUC"),
    Cedula(12, "Cédula de identidad"),
    Pasaporte(13, "Pasaporte"),
    CedulaExtranjero(14, "Cédula de extranjero"),
    /** Venta a consumidor final que no se identificó. */
    SinNombre(15, "Sin nombre"),
    Diplomatico(16, "Diplomático"),
    IdentificacionTributaria(17, "Identificación tributaria");

    private final int codigo;
    private final String etiqueta;

    TipoIdentificacion(int codigo, String etiqueta) {
        this.codigo = codigo;
        this.etiqueta = etiqueta;
    }

    public int codigo() {
        return codigo;
    }

    public String etiqueta() {
        return etiqueta;
    }

    /**
     * Si la DNIT exige informar el nombre o razón social. Para RUC y cédula
     * paraguaya lo saca de sus propios registros; para "sin nombre" no hay
     * nombre que informar.
     */
    public boolean exigeNombre() {
        return this != RUC && this != Cedula && this != SinNombre;
    }

    public static TipoIdentificacion porCodigo(int codigo) {
        for (TipoIdentificacion tipo : values()) {
            if (tipo.codigo == codigo) {
                return tipo;
            }
        }
        throw new IllegalArgumentException(
                "No existe tipo de identificación con código " + codigo + " en la Tabla 3 de la DNIT");
    }

    /**
     * La columna guarda el código de la DNIT y no el ordinal del enum: es el
     * número que se declara, y sigue significando lo mismo aunque mañana se
     * agregue un tipo nuevo en el medio.
     */
    @Converter
    public static class ConversorJpa implements AttributeConverter<TipoIdentificacion, Short> {

        @Override
        public Short convertToDatabaseColumn(TipoIdentificacion tipo) {
            return tipo == null ? null : (short) tipo.codigo;
        }

        @Override
        public TipoIdentificacion convertToEntityAttribute(Short codigo) {
            return codigo == null ? null : porCodigo(codigo);
        }
    }
}
