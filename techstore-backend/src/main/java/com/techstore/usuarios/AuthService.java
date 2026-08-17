package com.techstore.usuarios;

import com.techstore.comun.BusinessException;
import com.techstore.comun.RucParaguayo;
import com.techstore.notificaciones.CorreoService;
import com.techstore.notificaciones.EmailTemplateService;
import com.techstore.notificaciones.TipoCorreo;
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
    private final RefreshSessionService refreshSessionService;
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
            throw new BusinessException("usuarios.email.duplicado", request.email());
        }
        Rol rolCliente = rolRepository.findByNombre(ROL_CLIENTE)
                .orElseThrow(() -> new BusinessException("usuarios.rol.cliente.faltante"));

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
            throw new CuentaInactivaException("usuarios.cuenta.inactiva");
        }

        usuario.setLastLogin(LocalDateTime.now());
        usuarioRepository.save(usuario);

        return buildAuthResponse(usuario);
    }

    @Transactional(noRollbackFor = BadCredentialsException.class)
    public AuthResponse refresh(RefreshTokenRequest request) {
        RefreshSessionService.Rotacion rotacion = refreshSessionService.rotar(request.refreshToken());
        return buildAuthResponse(rotacion.usuario(), rotacion.refreshToken());
    }

    @Transactional
    public void logout(RefreshTokenRequest request) {
        refreshSessionService.revocar(request.refreshToken());
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
            String motivo = RucParaguayo.motivoInvalidez(ruc);
            if (motivo != null) {
                throw new BusinessException("facturacion.ruc.invalido", ruc, motivo);
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
        refreshSessionService.revocarTodas(usuario);

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
        return buildAuthResponse(usuario, refreshSessionService.crear(usuario));
    }

    private AuthResponse buildAuthResponse(Usuario usuario, String refreshToken) {
        return new AuthResponse(
                jwtService.generateAccessToken(usuario),
                refreshToken,
                "Bearer",
                jwtService.getAccessExpirationMs(),
                UsuarioResponse.from(usuario));
    }
}
