package com.techstore.ventas;

/** La revisión de un comprobante de pago puntual. */
public enum EstadoComprobante {
    EN_REVISION,
    APROBADO,
    /** Queda guardado con el motivo: es el respaldo de por qué se pidió otro. */
    RECHAZADO
}
