package com.techstore.entity;

/** Motivo por el que se envía una notificación por correo. */
public enum TipoCorreo {
    FACTURA("Factura emitida"),
    BIENVENIDA("Bienvenida"),
    CAMBIO_PASSWORD("Cambio de contraseña"),
    RESETEO_PASSWORD("Reseteo de contraseña"),
    CUENTA_DESACTIVADA("Cuenta desactivada");

    private final String descripcion;

    TipoCorreo(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getDescripcion() {
        return descripcion;
    }
}
