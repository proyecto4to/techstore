package com.techstore.usuarios;

import com.techstore.seguridad.PermisoService;
import com.techstore.seguridad.PermisosEfectivosResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
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
    private final PasswordRecoveryService passwordRecoveryService;
    private final ReautenticacionService reautenticacionService;
    private final PermisoService permisoService;

    @PostMapping("/password/forgot")
    @SecurityRequirements
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Solicitar recuperación de contraseña",
            description = "Siempre responde igual para no revelar si el correo existe")
    public void solicitarRecuperacion(@Valid @RequestBody SolicitarRecuperacionRequest request) {
        passwordRecoveryService.solicitar(request.email());
    }

    @PostMapping("/password/reset")
    @SecurityRequirements
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Completar recuperación de contraseña",
            description = "Consume un token aleatorio de un solo uso y revoca las sesiones existentes")
    public void completarRecuperacion(@Valid @RequestBody CompletarRecuperacionRequest request) {
        passwordRecoveryService.completar(request);
    }

    @PostMapping("/reautenticar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Confirmar mi identidad con la contraseña",
            description = "Respaldo de la confirmación biométrica y paso previo a tocar el correo saliente. "
                    + "Comprueba siempre al usuario de la sesión, nunca a otro, y usa el mismo "
                    + "limitador de intentos que el login")
    public void reautenticar(@Valid @RequestBody ReautenticacionRequest request, Authentication auth) {
        reautenticacionService.verificar(auth.getName(), request.password());
    }

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
    @SecurityRequirements
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crear cuenta", description = "Alta pública de clientes; devuelve tokens con la sesión ya iniciada")
    public AuthResponse registrar(@Valid @RequestBody RegistroRequest request) {
        return authService.registrar(request);
    }

    @PostMapping("/login")
    @SecurityRequirements
    @Operation(summary = "Iniciar sesión", description = "Devuelve un access token (corto) y un refresh token (largo)")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/refresh")
    @SecurityRequirements
    @Operation(summary = "Renovar tokens", description = "Emite un nuevo par de tokens a partir de un refresh token válido")
    public AuthResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return authService.refresh(request);
    }

    @PostMapping("/logout")
    @SecurityRequirements
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Cerrar sesión", description = "Revoca la sesión refresh actual")
    public void logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request);
    }

    @GetMapping("/me")
    @Operation(summary = "Perfil actual", description = "Datos del usuario autenticado")
    public UsuarioResponse me(Authentication authentication) {
        return authService.me(authentication.getName());
    }

    /**
     * Los permisos de quien está preguntando.
     *
     * <p>Vive acá y no en {@code /api/v1/seguridad} por una razón concreta: ese
     * prefijo pertenece al módulo Seguridad, que los roles que no administran
     * tienen cerrado. Preguntar qué puedo hacer yo no es administrar seguridad
     * —lo necesita cualquiera para dibujar su propio menú— y tiene que
     * responder siempre.
     *
     * <p>Es la única fuente de la que sacan sus permisos la Web y el APK.
     * Ninguno de los dos decide nada por su cuenta: lo que muestran sale de
     * acá, y lo que de verdad restringe es el backend en cada llamada.
     */
    @GetMapping("/mis-permisos")
    @Operation(summary = "Qué puedo hacer yo",
            description = "Permisos efectivos del usuario autenticado, con el origen de cada uno. "
                    + "Sirve para dibujar menús y botones; la autorización real la aplica el backend "
                    + "en cada pedido")
    public PermisosEfectivosResponse misPermisos(Authentication authentication) {
        return permisoService.mios(authentication.getName());
    }
}
