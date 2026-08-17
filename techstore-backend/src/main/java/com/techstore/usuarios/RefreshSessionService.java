package com.techstore.usuarios;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class RefreshSessionService {

    public record Rotacion(Usuario usuario, String refreshToken) {
    }

    private static final String REFRESH_INVALIDO = "Refresh token inválido o expirado";

    private final SesionRefreshRepository repository;
    private final JwtService jwtService;

    public String crear(Usuario usuario) {
        TokenEmitido emitido = emitir(usuario);
        repository.save(emitido.sesion());
        return emitido.token();
    }

    @Transactional(noRollbackFor = BadCredentialsException.class)
    public Rotacion rotar(String tokenActual) {
        Claims claims = claimsValidos(tokenActual);
        SesionRefresh actual = buscarSesion(claims);
        validarPertenencia(actual, claims, tokenActual);

        LocalDateTime ahora = ahoraUtc();
        if (actual.getRevocadoEn() != null) {
            // Reutilizar un token ya rotado sugiere robo o una carrera entre
            // clientes. Se cierran las demás sesiones del usuario.
            revocarTodas(actual.getUsuario(), ahora);
            throw credencialesInvalidas();
        }
        if (!actual.getExpiraEn().isAfter(ahora) || !Boolean.TRUE.equals(actual.getUsuario().getActivo())) {
            actual.setRevocadoEn(ahora);
            repository.save(actual);
            throw credencialesInvalidas();
        }

        TokenEmitido reemplazo = emitir(actual.getUsuario());
        actual.setRevocadoEn(ahora);
        actual.setReemplazadoPor(reemplazo.sesion().getTokenId());
        repository.save(actual);
        repository.save(reemplazo.sesion());
        return new Rotacion(actual.getUsuario(), reemplazo.token());
    }

    public void revocar(String token) {
        Claims claims = claimsValidos(token);
        SesionRefresh sesion = buscarSesion(claims);
        validarPertenencia(sesion, claims, token);
        if (sesion.getRevocadoEn() == null) {
            sesion.setRevocadoEn(ahoraUtc());
            repository.save(sesion);
        }
    }

    public void revocarTodas(Usuario usuario) {
        revocarTodas(usuario, ahoraUtc());
    }

    private void revocarTodas(Usuario usuario, LocalDateTime ahora) {
        var sesiones = repository.findAllByUsuarioIdAndRevocadoEnIsNull(usuario.getId());
        for (SesionRefresh sesion : sesiones) {
            sesion.setRevocadoEn(ahora);
        }
        repository.saveAll(sesiones);
    }

    private TokenEmitido emitir(Usuario usuario) {
        String token = jwtService.generateRefreshToken(usuario);
        Claims claims = claimsValidos(token);

        SesionRefresh sesion = new SesionRefresh();
        sesion.setTenantId(usuario.getTenantId() == null ? 1L : usuario.getTenantId());
        sesion.setUsuario(usuario);
        sesion.setTokenId(tokenId(claims));
        sesion.setTokenHash(hash(token));
        sesion.setCreadoEn(LocalDateTime.ofInstant(claims.getIssuedAt().toInstant(), ZoneOffset.UTC));
        sesion.setExpiraEn(LocalDateTime.ofInstant(claims.getExpiration().toInstant(), ZoneOffset.UTC));
        return new TokenEmitido(token, sesion);
    }

    private Claims claimsValidos(String token) {
        try {
            Claims claims = jwtService.parseToken(token);
            if (!jwtService.isRefreshToken(claims) || claims.getId() == null) {
                throw credencialesInvalidas();
            }
            return claims;
        } catch (JwtException | IllegalArgumentException ex) {
            throw credencialesInvalidas();
        }
    }

    private SesionRefresh buscarSesion(Claims claims) {
        return repository.buscarParaActualizar(tokenId(claims))
                .orElseThrow(this::credencialesInvalidas);
    }

    private void validarPertenencia(SesionRefresh sesion, Claims claims, String token) {
        Usuario usuario = sesion.getUsuario();
        Long tenantClaim = claims.get(JwtService.CLAIM_TENANT, Long.class);
        Long usuarioClaim = claims.get("uid", Long.class);
        boolean coincide = MessageDigest.isEqual(
                sesion.getTokenHash().getBytes(StandardCharsets.US_ASCII),
                hash(token).getBytes(StandardCharsets.US_ASCII));
        if (!coincide
                || !usuario.getId().equals(usuarioClaim)
                || !usuario.getEmail().equals(claims.getSubject())
                || !sesion.getTenantId().equals(tenantClaim)) {
            throw credencialesInvalidas();
        }
    }

    private UUID tokenId(Claims claims) {
        try {
            return UUID.fromString(claims.getId());
        } catch (RuntimeException ex) {
            throw credencialesInvalidas();
        }
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

    private BadCredentialsException credencialesInvalidas() {
        return new BadCredentialsException(REFRESH_INVALIDO);
    }

    private record TokenEmitido(String token, SesionRefresh sesion) {
    }
}
