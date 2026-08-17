package com.techstore.compras;

/**
 * Por dónde se le manda el pedido al proveedor.
 *
 * <p>Los nombres son los que guarda PostgreSQL en el tipo {@code canal_envio}.
 *
 * <p>Hoy solo el correo está operativo. WhatsApp figura porque el canal es
 * parte del modelo desde el principio, no porque funcione: mandar un mensaje
 * por WhatsApp en nombre de un negocio exige una cuenta de <b>WhatsApp Business
 * API</b> con un proveedor habilitado, con verificación del negocio y costo por
 * conversación. Un WhatsApp común no sirve, y automatizarlo por fuera de la API
 * oficial es motivo de bloqueo de la cuenta.
 */
public enum CanalEnvio {

    Correo("Correo electrónico"),
    WhatsApp("WhatsApp"),
    Ambos("Correo y WhatsApp");

    private final String etiqueta;

    CanalEnvio(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String etiqueta() {
        return etiqueta;
    }

    public boolean incluyeCorreo() {
        return this == Correo || this == Ambos;
    }

    public boolean incluyeWhatsApp() {
        return this == WhatsApp || this == Ambos;
    }
}
