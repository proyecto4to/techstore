package com.techstore.configuracion;

import com.techstore.comun.tenant.TenantContext;
import com.techstore.empresa.Configuracion;
import com.techstore.empresa.ConfiguracionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

/** Migra una contraseña SMTP antigua a AES-GCM al primer arranque de prod. */
@Component
@Profile("prod")
@Order(15)
@Slf4j
public class SecretosProduccionGuard implements ApplicationRunner {

    static final String SMTP_PASSWORD = "smtp.password";

    private final ConfiguracionRepository repository;
    private final SecretValueCipher cipher;
    private final TransactionOperations transactions;

    public SecretosProduccionGuard(
            ConfiguracionRepository repository,
            SecretValueCipher cipher,
            TransactionOperations transactions) {
        this.repository = repository;
        this.cipher = cipher;
        this.transactions = transactions;
        if (!cipher.habilitado()) {
            throw new IllegalStateException(
                    "El perfil prod requiere APP_DATA_ENCRYPTION_KEY (32 bytes aleatorios en Base64)");
        }
    }

    @Override
    public void run(ApplicationArguments args) {
        TenantContext.fijar(TenantContext.TENANT_POR_DEFECTO);
        try {
            transactions.executeWithoutResult(estado -> migrarPasswordSmtp());
        } finally {
            TenantContext.limpiar();
        }
    }

    private void migrarPasswordSmtp() {
        repository.findById(SMTP_PASSWORD).ifPresent(config -> {
            String valor = config.getValor();
            if (valor == null || valor.isBlank() || cipher.estaCifrado(valor)) {
                return;
            }
            config.setValor(cipher.cifrar(valor));
            repository.save(config);
            log.info("La contraseña SMTP existente fue migrada a almacenamiento cifrado");
        });
    }
}
