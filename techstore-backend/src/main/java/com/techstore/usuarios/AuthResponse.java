package com.techstore.usuarios;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresInMs,
        UsuarioResponse usuario) {
}
