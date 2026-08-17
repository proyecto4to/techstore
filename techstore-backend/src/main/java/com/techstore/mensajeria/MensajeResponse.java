package com.techstore.mensajeria;

import java.time.LocalDateTime;

public record MensajeResponse(
        Long id,
        Long conversacionId,
        String clientMessageId,
        String tipo,
        String contenido,
        Long remitenteId,
        String remitenteNombre,
        String remitenteEmail,
        String remitenteRol,
        LocalDateTime enviadoEn,
        LocalDateTime entregadoEn,
        LocalDateTime leidoEn) { }
