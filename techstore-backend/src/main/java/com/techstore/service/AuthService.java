package com.techstore.service;

import com.techstore.dto.request.CambioPasswordRequest;
import com.techstore.dto.request.DatosFiscalesRequest;
import com.techstore.dto.request.LoginRequest;
import com.techstore.dto.request.RefreshTokenRequest;
import com.techstore.dto.request.RegistroRequest;
import com.techstore.dto.response.AuthResponse;
import com.techstore.dto.response.UsuarioResponse;
import com.techstore.entity.Rol;
import com.techstore.entity.TipoCorreo;
import com.techstore.entity.Usuario;
import com.techstore.exception.BusinessException;
import com.techstore.exception.CuentaInactivaException;
import com.techstore.repository.RolRepository;
import com.techstore.repository.UsuarioRepository;
import com.techstore.security.JwtService;
import com.techstore.security.LoginRateLimiter;
import com.techstore.util.RucParaguayo;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String CREDENCIALES_INVALIDAS = "Email o contraseña incorrectos";

    private static final String ROL_CLIENTE = "Cliente";

    private final UsuarioRepository usuarioRepository;
    private final RolRepository rolRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final CorreoService correoService;
    private final EmailTemplateService emailTemplateService;
    private final LoginRateLimiter loginRateLimiter;

    /**
     * Alta pública de clientes: crea el usuario con rol Cliente y devuelve
     * tokens para que la sesión quede iniciada inmediatamente.
     */
    @Transactional
    public AuthResponse registrar(RegistroRequest request) {
        if (usuarioRepository.existsByEmail(request.email())) {
            throw new BusinessException("Ya existe una cuenta con el email " + request.email());
        }
        Rol rolCliente = rolRepository.findByNombre(ROL_CLIENTE)
                .orElseThrow(() -> new BusinessException("El rol Cliente no está configurado en el sistema"));

        Usuario usuario = new Usuario();
        usuario.setEmail(request.email());
        usuario.setPasswordHash(passwordEncoder.encode(request.password()));
        usuario.setNombre(request.nombre());
        usuario.setApellido(request.apellido());
        usuario.setRol(rolCliente);
        usuario.setActivo(true);
        usuario.setLastLogin(LocalDateTime.now());
        usuario = usuarioRepository.save(usuario);

        correoService.enviar(
                TipoCorreo.BIENVENIDA,
                usuario.getEmail(),
                usuario.getNombre() + " " + usuario.getApellido(),
                "¡Bienvenido a TechStore!",
                emailTemplateService.construir(
                        TipoCorreo.BIENVENIDA,
                        "¡Cuenta creada con éxito!",
                        usuario.getNombre(),
                        "Tu cuenta fue creada correctamente. Ya podés iniciar sesión y comprar en TechStore.",
                        null,
                        "IR A LA TIENDA",
                        emailTemplateService.url("/")),
                usuario.getEmail());

        return buildAuthResponse(usuario);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        loginRateLimiter.verificarDisponible(request.email());

        Usuario usuario = usuarioRepository.findByEmail(request.email()).orElse(null);

        // La contraseña se valida primero: así solo el dueño de la cuenta (que la
        // conoce) llega a enterarse de que fue bloqueada, sin filtrar ese dato a extraños.
        if (usuario == null || !passwordEncoder.matches(request.password(), usuario.getPasswordHash())) {
            loginRateLimiter.registrarFallo(request.email());
            throw new BadCredentialsException(CREDENCIALES_INVALIDAS);
        }
        loginRateLimiter.registrarExito(request.email());

        if (!Boolean.TRUE.equals(usuario.getActivo())) {
            throw new CuentaInactivaException(
                    "Tu cuenta está bloqueada o inactiva. Contactá al administrador para reactivarla.");
        }

        usuario.setLastLogin(LocalDateTime.now());
        usuarioRepository.save(usuario);

        return buildAuthResponse(usuario);
    }

    @Transactional(readOnly = true)
    public AuthResponse refresh(RefreshTokenRequest request) {
        Claims claims;
        try {
            claims = jwtService.parseToken(request.refreshToken());
        } catch (JwtException ex) {
            throw new BadCredentialsException("Refresh token inválido o expirado");
        }

        if (!jwtService.isRefreshToken(claims)) {
            throw new BadCredentialsException("El token provisto no es un refresh token");
        }

        Usuario usuario = usuarioRepository.findByEmail(claims.getSubject())
                .filter(u -> Boolean.TRUE.equals(u.getActivo()))
                .orElseThrow(() -> new BadCredentialsException("Usuario inexistente o inactivo"));

        return buildAuthResponse(usuario);
    }

    /**
     * Guarda (o borra) los datos fiscales propios, para que las próximas compras
     * se facturen a ese RUC sin tener que dictarlo en la caja.
     */
    @Transactional
    public UsuarioResponse guardarDatosFiscales(String email, DatosFiscalesRequest request) {
        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("Usuario no encontrado"));

        String ruc = RucParaguayo.normalizar(request.ruc());
        if (ruc == null) {
            // Sin RUC: vuelve a facturar como consumidor final.
            usuario.setRuc(null);
            usuario.setRazonSocial(null);
        } else {
            if (!RucParaguayo.esValido(ruc)) {
                throw new BusinessException(
                        "El RUC '%s' no es válido: revisá el número y el dígito verificador.".formatted(ruc));
            }
            usuario.setRuc(ruc);
            String razonSocial = request.razonSocial();
            usuario.setRazonSocial(razonSocial == null || razonSocial.isBlank() ? null : razonSocial.trim());
        }
        return UsuarioResponse.from(usuarioRepository.save(usuario));
    }

    @Transactional
    public void cambiarPassword(String email, CambioPasswordRequest request) {
        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("Usuario no encontrado"));
        if (!passwordEncoder.matches(request.passwordActual(), usuario.getPasswordHash())) {
            throw new BadCredentialsException("La contraseña actual es incorrecta");
        }
        usuario.setPasswordHash(passwordEncoder.encode(request.passwordNueva()));
        usuarioRepository.save(usuario);

        correoService.enviar(
                TipoCorreo.CAMBIO_PASSWORD,
                usuario.getEmail(),
                usuario.getNombre() + " " + usuario.getApellido(),
                "Tu contraseña de TechStore fue cambiada",
                emailTemplateService.construir(
                        TipoCorreo.CAMBIO_PASSWORD,
                        "Contraseña actualizada",
                        usuario.getNombre(),
                        "Te avisamos que tu contraseña se cambió correctamente.",
                        "Si no fuiste vos, contactá al administrador de inmediato.",
                        "IR A MI CUENTA",
                        emailTemplateService.url("/cuenta")),
                usuario.getEmail());
    }

    @Transactional(readOnly = true)
    public UsuarioResponse me(String email) {
        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("Usuario no encontrado"));
        return UsuarioResponse.from(usuario);
    }

    private AuthResponse buildAuthResponse(Usuario usuario) {
        return new AuthResponse(
                jwtService.generateAccessToken(usuario),
                jwtService.generateRefreshToken(usuario),
                "Bearer",
                jwtService.getAccessExpirationMs(),
                UsuarioResponse.from(usuario));
    }
}
