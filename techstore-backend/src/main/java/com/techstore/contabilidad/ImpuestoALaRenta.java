package com.techstore.contabilidad;

/**
 * A qué impuesto a la renta se imputan los comprobantes del período: son los
 * campos 16 y 17 del Registro de Comprobantes.
 *
 * <p>Lo elige quien exporta y no lo deduce el sistema, porque depende de las
 * obligaciones que el contribuyente tenga inscriptas en su RUC. Una persona
 * jurídica siempre está en IRE, pero una persona física puede estar en
 * cualquiera de los dos, y el sistema no guarda ese dato.
 */
public enum ImpuestoALaRenta {

    /** Impuesto a la Renta Empresarial. */
    IRE,

    /** Impuesto a la Renta Personal — Rentas y Servicios Personales. */
    IRP
}
