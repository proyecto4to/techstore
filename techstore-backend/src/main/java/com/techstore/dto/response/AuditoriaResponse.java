package com.techstore.dto.response;

import com.techstore.entity.AuditLog;

import java.time.LocalDateTime;
import java.util.Map;

public record AuditoriaResponse(
        Long id,
        String entidad,
        Long entidadId,
        String accion,
        String detalle,
        String usuarioEmail,
        Map<String, Object> cambios,
        LocalDateTime fecha) {

    public static AuditoriaResponse from(AuditLog log) {
        return new AuditoriaResponse(
                log.getId(),
                log.getEntidad(),
                log.getEntidadId(),
                log.getAccion(),
                log.getDetalle(),
                // Puede no haber usuario: hay acciones que no nacen de una sesión.
                log.getUsuarioEmail() == null ? "sistema" : log.getUsuarioEmail(),
                log.getCambios(),
                log.getFecha());
    }
}
