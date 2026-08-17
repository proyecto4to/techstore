package com.techstore.notificaciones;

/** Motivo por el que se envía una notificación por correo. */
public enum TipoCorreo {
    FACTURA("Factura emitida", "#10B981"),
    PEDIDO("Pedido recibido", "#0F66E6"),
    ESTADO_PEDIDO("Estado del pedido", "#38BDF8"),
    ENVIO("Seguimiento del envío", "#22C55E"),
    MENSAJE("Nuevo mensaje", "#A78BFA"),
    BIENVENIDA("Bienvenida", "#7C3AED"),
    /** El único que no va a un cliente: es el pedido que se le manda al proveedor. */
    PEDIDO_PROVEEDOR("Pedido a proveedor", "#F97316"),
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

    /**
     * Si este correo espera que le contesten.
     *
     * <p>Casi todos son avisos y su pie dice "no respondas a este correo". El
     * pedido a proveedor es al revés: la respuesta —confirmando disponibilidad y
     * plazo, o proponiendo cambios— es justamente lo que se busca.
     */
    public boolean esperaRespuesta() {
        return this == PEDIDO_PROVEEDOR;
    }

    /** Color de acento de la plantilla HTML del correo (ver EmailTemplateService). */
    public String getColorHex() {
        return colorHex;
    }
}
