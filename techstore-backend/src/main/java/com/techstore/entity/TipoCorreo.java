package com.techstore.entity;

/** Motivo por el que se envía una notificación por correo. */
public enum TipoCorreo {
    FACTURA("Factura emitida", "#10B981"),
    PEDIDO("Pedido recibido", "#0F66E6"),
    BIENVENIDA("Bienvenida", "#7C3AED"),
    CAMBIO_PASSWORD("Cambio de contraseña", "#14B8A6"),
    RESETEO_PASSWORD("Reseteo de contraseña", "#F59E0B"),
    CUENTA_DESACTIVADA("Cuenta desactivada", "#EF4444");

    private final String descripcion;
    private final String colorHex;

    TipoCorreo(String descripcion, String colorHex) {
        this.descripcion = descripcion;
        this.colorHex = colorHex;
    }

    public String getDescripcion() {
        return descripcion;
    }

    /** Color de acento de la plantilla HTML del correo (ver EmailTemplateService). */
    public String getColorHex() {
        return colorHex;
    }
}
