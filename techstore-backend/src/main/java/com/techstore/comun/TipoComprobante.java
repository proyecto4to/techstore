package com.techstore.comun;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.EnumSet;
import java.util.Set;

/**
 * Qué clase de documento fiscal es: Tabla 4 del Registro de Comprobantes de la
 * DNIT (RG 90/2021).
 *
 * <p>Están solo los que corresponden a ventas y compras (códigos 101 a 112).
 * La tabla de la DNIT sigue con los códigos 201 a 211 —liquidación de salario,
 * extracto de IPS, giros bancarios—, que pertenecen a los registros de ingresos
 * y egresos de quienes tributan IRP. Un sistema de facturación no los genera,
 * así que agregarlos sería vocabulario muerto.
 *
 * <p>Cada valor sabe qué exige la especificación para él. Están acá y no
 * desperdigados en el exportador porque son reglas del comprobante, no del
 * archivo: la validación de una compra cargada a mano las necesita igual.
 */
public enum TipoComprobante {

    Autofactura(101, "Autofactura", Uso.COMPRAS),
    BoletaTransportePublico(102, "Boleta de transporte público de pasajeros", Uso.AMBOS),
    BoletaVenta(103, "Boleta de venta", Uso.AMBOS),
    BoletaResimple(104, "Boleta RESIMPLE", Uso.COMPRAS),
    BoletoLoteria(105, "Boletos de loterías y juegos de azar", Uso.AMBOS),
    BoletoTransporteAereo(106, "Boleto o ticket de transporte aéreo", Uso.AMBOS),
    DespachoImportacion(107, "Despacho de importación", Uso.COMPRAS),
    EntradaEspectaculo(108, "Entrada a espectáculos públicos", Uso.AMBOS),
    Factura(109, "Factura", Uso.AMBOS),
    NotaCredito(110, "Nota de crédito", Uso.AMBOS),
    NotaDebito(111, "Nota de débito", Uso.AMBOS),
    TicketMaquinaRegistradora(112, "Ticket de máquina registradora", Uso.AMBOS);

    private enum Uso {VENTAS, COMPRAS, AMBOS}

    /**
     * Los que se informan con el total solo, sin abrir por tasa de IVA. No dan
     * crédito fiscal, así que la DNIT no quiere el desglose: pide ceros en las
     * tres columnas y el importe únicamente en el total.
     */
    private static final Set<TipoComprobante> SIN_DESGLOSE =
            EnumSet.of(Autofactura, BoletaResimple, BoletoLoteria, TicketMaquinaRegistradora);

    /** Los que corrigen a otro documento y tienen que decir a cuál. */
    private static final Set<TipoComprobante> CORRECTIVOS =
            EnumSet.of(NotaCredito, NotaDebito);

    /** No llevan número con el formato ###-###-#######. */
    private static final Set<TipoComprobante> SIN_NUMERO =
            EnumSet.of(TicketMaquinaRegistradora, BoletoTransporteAereo, DespachoImportacion);

    private final int codigo;
    private final String etiqueta;
    private final Uso uso;

    TipoComprobante(int codigo, String etiqueta, Uso uso) {
        this.codigo = codigo;
        this.etiqueta = etiqueta;
        this.uso = uso;
    }

    public int codigo() {
        return codigo;
    }

    public String etiqueta() {
        return etiqueta;
    }

    public boolean sirveParaVentas() {
        return uso == Uso.VENTAS || uso == Uso.AMBOS;
    }

    public boolean sirveParaCompras() {
        return uso == Uso.COMPRAS || uso == Uso.AMBOS;
    }

    /** Si se informa abierto por tasa de IVA o solamente el importe total. */
    public boolean llevaDesglose() {
        return !SIN_DESGLOSE.contains(this);
    }

    public boolean exigeNumero() {
        return !SIN_NUMERO.contains(this);
    }

    /** El despacho de importación no tiene timbrado: se declara con cero. */
    public boolean exigeTimbrado() {
        return this != DespachoImportacion;
    }

    /** Las notas de crédito y de débito tienen que decir qué documento corrigen. */
    public boolean exigeAsociado() {
        return CORRECTIVOS.contains(this);
    }

    /** Resta en el libro en vez de sumar. */
    public boolean esCorrectivoNegativo() {
        return this == NotaCredito;
    }

    public static TipoComprobante porCodigo(int codigo) {
        for (TipoComprobante tipo : values()) {
            if (tipo.codigo == codigo) {
                return tipo;
            }
        }
        throw new IllegalArgumentException(
                "No existe tipo de comprobante con código " + codigo + " en la Tabla 4 de la DNIT");
    }

    /**
     * La columna guarda el código de la DNIT y no el ordinal del enum: es el
     * número que se declara, y sigue significando lo mismo aunque mañana se
     * agregue un tipo nuevo en el medio.
     */
    @Converter
    public static class ConversorJpa implements AttributeConverter<TipoComprobante, Short> {

        @Override
        public Short convertToDatabaseColumn(TipoComprobante tipo) {
            return tipo == null ? null : (short) tipo.codigo;
        }

        @Override
        public TipoComprobante convertToEntityAttribute(Short codigo) {
            return codigo == null ? null : porCodigo(codigo);
        }
    }
}
