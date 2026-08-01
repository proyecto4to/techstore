package com.techstore.usuarios;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Autenticación", description = "Login, renovación de tokens y perfil del usuario autenticado")
public class AuthController {

    private final AuthService authService;

    @PutMapping("/password")
    @Operation(summary = "Cambiar mi contraseña",
            description = "Requiere la contraseña actual; disponible para cualquier usuario autenticado")
    public void cambiarPassword(@Valid @RequestBody CambioPasswordRequest request, Authentication auth) {
        authService.cambiarPassword(auth.getName(), request);
    }

    @PutMapping("/datos-fiscales")
    @Operation(summary = "Guardar mis datos fiscales",
            description = "RUC y razón social para facturar como contribuyente; vacío vuelve a consumidor final")
    public UsuarioResponse guardarDatosFiscales(
            @Valid @RequestBody DatosFiscalesRequest request, Authentication auth) {
        return authService.guardarDatosFiscales(auth.getName(), request);
    }

    @PostMapping("/registro")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crear cuenta", description = "Alta pública de clientes; devuelve tokens con la sesión ya iniciada")
    public AuthResponse registrar(@Valid @RequestBody RegistroRequest request) {
        return authService.registrar(request);
    }

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
