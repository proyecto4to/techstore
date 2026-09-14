package com.techstore.auditoria;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Un registro de auditoría, listo para mostrarse.
 *
 * <p>{@code cambios} es el antes y el después de cada campo. Llega como mapa
 * para que la pantalla lo arme como una tabla legible: mostrarlo como JSON
 * crudo obliga a leer llaves y comillas para entender qué pasó.
 */
public record AuditoriaResponse(
        Long id,
        String entidad,
        Long entidadId,
        String accion,
        String detalle,
        String usuarioEmail,
        /** El rol que tenía el autor al hacer la acción, no el de hoy. */
        String usuarioRol,
        Map<String, Object> cambios,
        /** Permitido o Rechazado. Nulo en los registros anteriores a la columna. */
        String resultado,
        /** Web, Movil o Api, segun lo declaró el cliente. */
        String plataforma,
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
                log.getUsuarioRol(),
                log.getCambios(),
                log.getResultado(),
                log.getPlataforma(),
                log.getFecha());
    }
}
