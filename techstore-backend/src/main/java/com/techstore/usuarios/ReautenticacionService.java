package com.techstore.usuarios;

import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Volver a comprobar la contraseña de quien ya tiene la sesión abierta.
 *
 * <p>Vive aparte de {@link AuthService} a propósito. Lo usan operaciones
 * sensibles de otros módulos —la configuración del correo saliente, entre
 * ellas—, y {@code AuthService} depende del servicio de correo: pedirle esto
 * directamente arma un ciclo de dependencias que impide levantar la aplicación.
 * Acá solo hacen falta el repositorio, el cifrador y el limitador de intentos.
 */
@Service
@RequiredArgsConstructor
public class ReautenticacionService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final LoginRateLimiter loginRateLimiter;

    /**
     * Comprueba la contraseña del usuario de la sesión en curso.
     *
     * <p>Pasa por el mismo limitador que el login: sin eso, un endpoint que
     * contesta «sí» o «no» a una contraseña es un oráculo para probarlas de a
     * miles, y encima desde una sesión ya válida.
     */
    @Transactional(readOnly = true)
    public void verificar(String email, String password) {
        loginRateLimiter.verificarDisponible(email);
        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("Usuario no encontrado"));
        if (!passwordEncoder.matches(password, usuario.getPasswordHash())) {
            loginRateLimiter.registrarFallo(email);
            throw new BadCredentialsException("La contraseña no coincide");
        }
        loginRateLimiter.registrarExito(email);
    }
}
