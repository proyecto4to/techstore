package com.techstore.controller;

import com.techstore.dto.request.LoginRequest;
import com.techstore.dto.request.RefreshTokenRequest;
import com.techstore.dto.response.AuthResponse;
import com.techstore.dto.response.UsuarioResponse;
import com.techstore.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Autenticación", description = "Login, renovación de tokens y perfil del usuario autenticado")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    @Operation(summary = "Iniciar sesión", description = "Devuelve un access token (corto) y un refresh token (largo)")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Renovar tokens", description = "Emite un nuevo par de tokens a partir de un refresh token válido")
    public AuthResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return authService.refresh(request);
    }

    @GetMapping("/me")
    @Operation(summary = "Perfil actual", description = "Datos del usuario autenticado")
    public UsuarioResponse me(Authentication authentication) {
        return authService.me(authentication.getName());
    }
}
