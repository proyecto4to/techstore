package com.techstore.usuarios;

import com.techstore.comun.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionOperations;

import java.util.Optional;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CredencialesProduccionGuardTest {

    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private TransactionOperations transactions;

    @AfterEach
    void limpiarTenant() {
        TenantContext.limpiar();
    }

    @Test
    void reemplazaAdmin123EnElPrimerArranqueComercial() {
        Usuario admin = admin();
        ejecutarTransaccionReal();
        when(usuarioRepository.findByEmail(CredencialesProduccionGuard.ADMIN_DEMO))
                .thenReturn(Optional.of(admin));
        when(passwordEncoder.matches(CredencialesProduccionGuard.PASSWORD_DEMO, "hash-demo"))
                .thenReturn(true);
        when(passwordEncoder.encode("ClaveComercial2026"))
                .thenReturn("hash-comercial");

        guard("ClaveComercial2026").run(null);

        verify(usuarioRepository).save(admin);
        org.junit.jupiter.api.Assertions.assertEquals("hash-comercial", admin.getPasswordHash());
        assertFalse(TenantContext.hayTenant(), "el runner no debe filtrar tenant al resto del arranque");
    }

    @Test
    void noPisaUnaPasswordQueElUsuarioYaCambio() {
        Usuario admin = admin();
        ejecutarTransaccionReal();
        when(usuarioRepository.findByEmail(CredencialesProduccionGuard.ADMIN_DEMO))
                .thenReturn(Optional.of(admin));
        when(passwordEncoder.matches(CredencialesProduccionGuard.PASSWORD_DEMO, "hash-demo"))
                .thenReturn(false);

        guard("").run(null);

        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void rechazaProduccionConLaClaveDemoYSinBootstrapSeguro() {
        Usuario admin = admin();
        ejecutarTransaccionReal();
        when(usuarioRepository.findByEmail(CredencialesProduccionGuard.ADMIN_DEMO))
                .thenReturn(Optional.of(admin));
        when(passwordEncoder.matches(CredencialesProduccionGuard.PASSWORD_DEMO, "hash-demo"))
                .thenReturn(true);

        assertThrows(IllegalStateException.class, () -> guard("reemplaza-esto").run(null));
        verify(usuarioRepository, never()).save(any());
        assertFalse(TenantContext.hayTenant());
    }

    @SuppressWarnings("unchecked")
    private void ejecutarTransaccionReal() {
        org.mockito.Mockito.doAnswer(invocacion -> {
            Consumer<TransactionStatus> accion = invocacion.getArgument(0);
            accion.accept(org.mockito.Mockito.mock(TransactionStatus.class));
            return null;
        }).when(transactions).executeWithoutResult(any());
    }

    private CredencialesProduccionGuard guard(String password) {
        return new CredencialesProduccionGuard(
                usuarioRepository, passwordEncoder, transactions, password);
    }

    private static Usuario admin() {
        Usuario admin = new Usuario();
        admin.setPasswordHash("hash-demo");
        return admin;
    }
}
