package com.techstore.ventas;

/**
 * A dónde se transfiere, tal como lo cargó la tienda.
 *
 * @param disponible si alcanza para transferir: banco, titular y una cuenta o
 *                   un alias. Sin eso la app no ofrece la transferencia, y
 *                   sin transferencia no hay envío a domicilio.
 * @param tieneQr    si la tienda subió el QR de su banco
 */
public record DatosTransferenciaResponse(
        boolean disponible,
        String banco,
        String titular,
        String documento,
        String numeroCuenta,
        String tipoCuenta,
        String alias,
        String instrucciones,
        boolean tieneQr) {
}
