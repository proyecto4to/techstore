package com.techstore.mensajeria;

import java.time.LocalDateTime;

public record LecturaResponse(Long conversacionId, String lectorEmail, int mensajes, LocalDateTime leidoEn) { }
