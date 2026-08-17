package com.techstore.mensajeria;

import java.time.LocalDateTime;

public record ChatErrorResponse(String codigo, String mensaje, LocalDateTime fechaHora) {
    public static ChatErrorResponse de(String codigo, String mensaje) {
        return new ChatErrorResponse(codigo, mensaje, LocalDateTime.now());
    }
}
