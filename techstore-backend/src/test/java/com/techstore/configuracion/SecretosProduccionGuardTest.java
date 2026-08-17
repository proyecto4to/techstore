package com.techstore.configuracion;

import com.techstore.comun.tenant.TenantContext;
import com.techstore.empresa.Configuracion;
import com.techstore.empresa.ConfiguracionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionOperations;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SecretosProduccionGuardTest {

    private static final String CLAVE = Base64.getEncoder().encodeToString(
            "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));

    @Mock
    private ConfiguracionRepository repository;
    @Mock
    private TransactionOperations transactions;

    @AfterEach
    void limpiarTenant() {
        TenantContext.limpiar();
    }

    @Test
    void migraUnaPasswordHeredadaEnClaroYLaDejaDescifrable() {
        SecretValueCipher cipher = new SecretValueCipher(CLAVE);
        Configuracion smtp = configuracion("clave-vieja-en-claro");
        ejecutarTransaccionReal();
        when(repository.findById(SecretosProduccionGuard.SMTP_PASSWORD))
                .thenReturn(Optional.of(smtp));

        new SecretosProduccionGuard(repository, cipher, transactions).run(null);

        verify(repository).save(smtp);
        assertTrue(cipher.estaCifrado(smtp.getValor()), "la password debe quedar cifrada en la base");
        assertEquals("clave-vieja-en-claro", cipher.descifrar(smtp.getValor()),
                "migrar no puede perder el secreto: el SMTP tiene que seguir autenticando");
        assertFalse(TenantContext.hayTenant(), "el runner no debe filtrar tenant al resto del arranque");
    }

    @Test
    void noVuelveACifrarUnaPasswordQueYaEstaCifrada() {
        SecretValueCipher cipher = new SecretValueCipher(CLAVE);
        // Un segundo arranque encuentra el valor ya migrado. Volver a cifrarlo
        // anidaría envoltorios y el descifrado devolvería basura con prefijo.
        Configuracion smtp = configuracion(cipher.cifrar("clave-ya-migrada"));
        ejecutarTransaccionReal();
        when(repository.findById(SecretosProduccionGuard.SMTP_PASSWORD))
                .thenReturn(Optional.of(smtp));

        new SecretosProduccionGuard(repository, cipher, transactions).run(null);

        verify(repository, never()).save(any());
        assertEquals("clave-ya-migrada", cipher.descifrar(smtp.getValor()));
    }

    @Test
    void noTocaUnaInstalacionQueTodaviaNoConfiguroElCorreo() {
        SecretValueCipher cipher = new SecretValueCipher(CLAVE);
        ejecutarTransaccionReal();
        when(repository.findById(SecretosProduccionGuard.SMTP_PASSWORD))
                .thenReturn(Optional.of(configuracion("")));

        new SecretosProduccionGuard(repository, cipher, transactions).run(null);

        verify(repository, never()).save(any());
    }

    @Test
    void seNiegaAArrancarProduccionSinClaveDeCifrado() {
        // Sin clave, la password SMTP quedaría en claro en la base sin que nadie
        // se entere. El arranque tiene que caerse antes, no después.
        SecretValueCipher sinClave = new SecretValueCipher("");

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> new SecretosProduccionGuard(repository, sinClave, transactions));

        assertTrue(error.getMessage().contains("APP_DATA_ENCRYPTION_KEY"),
                "el mensaje tiene que nombrar la variable que hay que definir");
    }

    @SuppressWarnings("unchecked")
    private void ejecutarTransaccionReal() {
        org.mockito.Mockito.doAnswer(invocacion -> {
            Consumer<TransactionStatus> accion = invocacion.getArgument(0);
            accion.accept(org.mockito.Mockito.mock(TransactionStatus.class));
            return null;
        }).when(transactions).executeWithoutResult(any());
    }

    private static Configuracion configuracion(String valor) {
        return new Configuracion(SecretosProduccionGuard.SMTP_PASSWORD, valor);
    }
}
