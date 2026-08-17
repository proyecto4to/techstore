package com.techstore.notificaciones;

import java.util.Map;

public record PushMessage(
        String token,
        String titulo,
        String cuerpo,
        String deepLink,
        Map<String, Object> datos) { }
