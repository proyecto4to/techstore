package com.techstore.notificaciones;

import java.time.LocalDateTime;

public record NotificacionUsuarioResponse(
        Long id,
        String tipo,
        String titulo,
        String mensaje,
        String referenciaTipo,
        Long referenciaId,
        String deepLink,
        boolean leida,
        LocalDateTime createdAt) {
    static NotificacionUsuarioResponse from(NotificacionUsuario value) {
        return new NotificacionUsuarioResponse(value.getId(), value.getTipo().name(), value.getTitulo(),
                value.getMensaje(), value.getReferenciaTipo(), value.getReferenciaId(), value.getDeepLink(),
                value.isLeida(), value.getCreatedAt());
    }
}
