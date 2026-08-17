package com.techstore.notificaciones;

import java.time.LocalDateTime;

/** No devuelve el push token: el cliente no necesita volver a leerlo. */
public record DispositivoMovilResponse(
        Long id,
        PlataformaMovil plataforma,
        String deviceIdentifier,
        boolean activo,
        LocalDateTime ultimoUso,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) { }
