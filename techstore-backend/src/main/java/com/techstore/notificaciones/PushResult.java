package com.techstore.notificaciones;

public record PushResult(boolean aceptado, boolean permanente, String ticketId, String codigo) {

    public static PushResult aceptado(String ticketId) {
        return new PushResult(true, false, ticketId, null);
    }

    public static PushResult omitido(String codigo) {
        return new PushResult(false, false, null, codigo);
    }

    public static PushResult error(String codigo, boolean permanente) {
        return new PushResult(false, permanente, null, codigo);
    }
}
