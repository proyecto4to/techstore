package com.techstore.usuarios;

import com.techstore.comun.BusinessException;
import com.techstore.notificaciones.CorreoService;
import com.techstore.notificaciones.EmailTemplateService;
import com.techstore.notificaciones.EstadoCorreo;
import com.techstore.notificaciones.TipoCorreo;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class PasswordRecoveryService {

    private final PasswordResetTokenRepository tokenRepository;
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshSessionService refreshSessionService;
    private final CorreoService correoService;
    private final EmailTemplateService emailTemplateService;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.password-reset-url:techstore://app/password/reset}")
    private String passwordResetUrl;

    @Value("${app.password-reset-minutes:15}")
    private long expirationMinutes;

    @Transactional
    public void solicitar(String email) {
        Usuario usuario = usuarioRepository.findByEmail(email.trim()).orElse(null);
        if (usuario == null || !Boolean.TRUE.equals(usuario.getActivo())) {
            return;
        }

        LocalDateTime ahora = ahoraUtc();
        if (tokenRepository.existsByUsuarioIdAndUsadoEnIsNullAndExpiraEnAfter(usuario.getId(), ahora)) {
            return;
        }

        String token = nuevoToken();
        PasswordResetToken entity = new PasswordResetToken();
        entity.setTenantId(usuario.getTenantId() == null ? 1L : usuario.getTenantId());
        entity.setUsuario(usuario);
        entity.setTokenHash(hash(token));
        entity.setCreadoEn(ahora);
        entity.setExpiraEn(ahora.plusMinutes(expirationMinutes));
        tokenRepository.save(entity);

        String enlace = UriComponentsBuilder.fromUriString(passwordResetUrl)
                .queryParam("token", token)
                .build()
                .encode()
                .toUriString();
        EstadoCorreo estado = correoService.enviar(
                TipoCorreo.RESETEO_PASSWORD,
                usuario.getEmail(),
                usuario.getNombre() + " " + usuario.getApellido(),
                "Recuperá tu contraseña de TechStore",
                emailTemplateService.construir(
                        TipoCorreo.RESETEO_PASSWORD,
                        "Recuperación de contraseña",
                        usuario.getNombre(),
                        "Recibimos una solicitud para cambiar tu contraseña. El enlace vence en "
                                + expirationMinutes + " minutos y solo puede usarse una vez.",
                        "Si no solicitaste este cambio, podés ignorar el mensaje.",
                        "CAMBIAR CONTRASEÑA",
                        enlace),
                usuario.getEmail());
        if (estado == EstadoCorreo.ERROR) {
            tokenRepository.delete(entity);
        }
    }

    @Transactional
    public void completar(CompletarRecuperacionRequest request) {
        PasswordResetToken token = tokenRepository.buscarParaActualizar(hash(request.token()))
                .orElseThrow(this::tokenInvalido);
        LocalDateTime ahora = ahoraUtc();
        if (token.getUsadoEn() != null
                || !token.getExpiraEn().isAfter(ahora)
                || !Boolean.TRUE.equals(token.getUsuario().getActivo())) {
            throw tokenInvalido();
        }

        Usuario usuario = token.getUsuario();
        usuario.setPasswordHash(passwordEncoder.encode(request.password()));
        usuarioRepository.save(usuario);
        token.setUsadoEn(ahora);
        tokenRepository.save(token);
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
                        "Tu contraseña se cambió mediante el proceso de recuperación.",
                        "Si no fuiste vos, contactá al administrador de inmediato.",
                        null,
                        null),
                usuario.getEmail());
    }

    private String nuevoToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 no está disponible", ex);
        }
    }

    private LocalDateTime ahoraUtc() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    private BusinessException tokenInvalido() {
        return new BusinessException("auth.recuperacion.invalida");
    }
}
