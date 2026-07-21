package com.techstore.dto.response;

import com.techstore.entity.Correo;

import java.time.LocalDateTime;

/** Vista de un correo de la bandeja de salida para el panel de administración. */
public record CorreoResponse(
        Long id,
        String tipo,
        String tipoDescripcion,
        String destinatario,
        String destinatarioNombre,
        String asunto,
        String estado,
        String referencia,
        boolean tieneAdjunto,
        String adjuntoNombre,
        String error,
        LocalDateTime fecha) {

    public static CorreoResponse from(Correo c) {
        return new CorreoResponse(
                c.getId(),
                c.getTipo().name(),
                c.getTipo().getDescripcion(),
                c.getDestinatario(),
                c.getDestinatarioNombre(),
                c.getAsunto(),
                c.getEstado().name(),
                c.getReferencia(),
                c.isTieneAdjunto(),
                c.getAdjuntoNombre(),
                c.getError(),
                c.getFecha());
    }
}
