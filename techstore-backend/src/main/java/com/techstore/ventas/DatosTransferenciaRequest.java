package com.techstore.ventas;

import jakarta.validation.constraints.Size;

/**
 * Los datos con los que el cliente transfiere. Un campo vacío lo borra.
 *
 * @param alias         el alias del SIPAP (cédula, celular o correo), si la cuenta tiene uno
 * @param instrucciones lo que la tienda quiera aclarar, por ejemplo qué poner en el concepto
 */
public record DatosTransferenciaRequest(
        @Size(max = 80) String banco,
        @Size(max = 120) String titular,
        @Size(max = 30) String documento,
        @Size(max = 40) String numeroCuenta,
        @Size(max = 40) String tipoCuenta,
        @Size(max = 80) String alias,
        @Size(max = 300) String instrucciones) {
}
