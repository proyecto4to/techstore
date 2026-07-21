package com.techstore.entity;

/** Resultado del intento de envío de un correo. */
public enum EstadoCorreo {
    /** Registrado, aún sin resolver (transitorio). */
    PENDIENTE,
    /** Enviado de verdad por SMTP. */
    ENVIADO,
    /** Registrado localmente sin SMTP configurado (modo demostrativo). */
    SIMULADO,
    /** Se intentó enviar pero el SMTP falló. */
    ERROR
}
