package com.techstore.configuracion;

import com.techstore.comun.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionOperations;

import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DatosDemoProduccionGuardTest {

    @Mock
    private JdbcOperations jdbc;
    @Mock
    private TransactionOperations transactions;

    @AfterEach
    void limpiarTenant() {
        TenantContext.limpiar();
    }

    @Test
    void fallaCerradoCuandoProdEncuentraDatosDemo() {
        ejecutarTransaccionReal();
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(42L);

        assertThrows(IllegalStateException.class, () -> guard("refuse").run(null));

        verify(jdbc, never()).update(anyString(), any(Object.class));
        assertFalse(TenantContext.hayTenant());
    }

    @Test
    void purgeProvisionaLaBaseSinFiltrarElTenant() {
        ejecutarTransaccionReal();
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(42L);

        guard("PURGE").run(null);

        verify(jdbc, atLeast(7)).update(anyString(), eq(TenantContext.TENANT_POR_DEFECTO));
        assertFalse(TenantContext.hayTenant());
    }

    @Test
    void rechazaUnaPoliticaAmbiguaAntesDeTocarLaBase() {
        assertThrows(IllegalArgumentException.class, () -> guard("si"));
        verify(transactions, never()).executeWithoutResult(any());
    }

    @SuppressWarnings("unchecked")
    private void ejecutarTransaccionReal() {
        doAnswer(invocacion -> {
            Consumer<TransactionStatus> accion = invocacion.getArgument(0);
            accion.accept(org.mockito.Mockito.mock(TransactionStatus.class));
            return null;
        }).when(transactions).executeWithoutResult(any());
    }

    private DatosDemoProduccionGuard guard(String policy) {
        return new DatosDemoProduccionGuard(jdbc, transactions, policy);
    }
}
