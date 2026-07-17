package com.techstore.service;

import com.techstore.dto.request.LoginRequest;
import com.techstore.dto.request.RefreshTokenRequest;
import com.techstore.dto.request.RegistroRequest;
import com.techstore.dto.response.AuthResponse;
import com.techstore.dto.response.UsuarioResponse;
import com.techstore.entity.Rol;
import com.techstore.entity.Usuario;
import com.techstore.exception.BusinessException;
import com.techstore.repository.RolRepository;
import com.techstore.repository.UsuarioRepository;
import com.techstore.security.JwtService;
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

        return buildAuthResponse(usuario);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        Usuario usuario = usuarioRepository.findByEmail(request.email())
                .orElseThrow(() -> new BadCredentialsException(CREDENCIALES_INVALIDAS));

        if (!Boolean.TRUE.equals(usuario.getActivo())
                || !passwordEncoder.matches(request.password(), usuario.getPasswordHash())) {
            throw new BadCredentialsException(CREDENCIALES_INVALIDAS);
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
