package com.techstore.usuarios;

import com.techstore.comun.tenant.TenantContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Impide que una instalación comercial quede publicada con la contraseña
 * conocida de los datos de demostración.
 *
 * <p>Solo actúa cuando el hash todavía corresponde a {@code admin123}. En el
 * primer arranque del perfil {@code prod} lo reemplaza por la contraseña
 * entregada fuera del repositorio; después nunca pisa un cambio hecho por el
 * usuario desde la aplicación.
 */
@Component
@Profile("prod")
@Order(20)
@Slf4j
public class CredencialesProduccionGuard implements ApplicationRunner {

    static final String ADMIN_DEMO = "admin@techstore.py";
    static final String PASSWORD_DEMO = "admin123";

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final TransactionOperations transactions;
    private final String bootstrapPassword;

    public CredencialesProduccionGuard(
            UsuarioRepository usuarioRepository,
            PasswordEncoder passwordEncoder,
            TransactionOperations transactions,
            @Value("${app.bootstrap-admin-password:}") String bootstrapPassword) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.transactions = transactions;
        this.bootstrapPassword = bootstrapPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        // La conexión de la transacción debe abrirse después de fijar el tenant;
        // de lo contrario RLS falla cerrado y no permite inspeccionar la cuenta.
        TenantContext.fijar(TenantContext.TENANT_POR_DEFECTO);
        try {
            transactions.executeWithoutResult(estado -> reemplazarPasswordDemo());
        } finally {
            TenantContext.limpiar();
        }
    }

    private void reemplazarPasswordDemo() {
        usuarioRepository.findByEmail(ADMIN_DEMO).ifPresent(admin -> {
            if (!passwordEncoder.matches(PASSWORD_DEMO, admin.getPasswordHash())) {
                return;
            }
            validarBootstrapPassword();
            admin.setPasswordHash(passwordEncoder.encode(bootstrapPassword));
            usuarioRepository.save(admin);
            log.warn("La contraseña de demostración del administrador fue reemplazada por la clave inicial comercial");
        });
    }

    private void validarBootstrapPassword() {
        boolean compleja = bootstrapPassword != null
                && bootstrapPassword.length() >= 12
                && bootstrapPassword.length() <= 100
                && bootstrapPassword.chars().anyMatch(Character::isLowerCase)
                && bootstrapPassword.chars().anyMatch(Character::isUpperCase)
                && bootstrapPassword.chars().anyMatch(Character::isDigit)
                && !PASSWORD_DEMO.equals(bootstrapPassword)
                && !bootstrapPassword.toLowerCase().contains("reemplaza");
        if (!compleja) {
            throw new IllegalStateException(
                    "El perfil prod detectó admin123. Define APP_BOOTSTRAP_ADMIN_PASSWORD "
                            + "con 12-100 caracteres, mayúscula, minúscula y número.");
        }
    }
}
