package com.techstore.comun.tenant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TenantDataSourceTest {

    @AfterEach
    void limpiar() {
        TenantContext.limpiar();
    }

    @Test
    void marcaCadaConexionConElTenantFirmado() throws Exception {
        DataSource delegado = mock(DataSource.class);
        Connection conexion = mock(Connection.class);
        PreparedStatement sentencia = mock(PreparedStatement.class);
        when(delegado.getConnection()).thenReturn(conexion);
        when(conexion.prepareStatement("SELECT set_config('app.tenant_id', ?, false)"))
                .thenReturn(sentencia);
        TenantContext.fijar(8L);

        Connection resultado = new TenantDataSource(delegado).getConnection();

        assertSame(conexion, resultado);
        verify(sentencia).setString(1, "8");
        verify(sentencia).execute();
    }

    @Test
    void descartaLaConexionSiNoPuedeAplicarElAislamiento() throws Exception {
        DataSource delegado = mock(DataSource.class);
        Connection conexion = mock(Connection.class);
        SQLException causa = new SQLException("set_config no disponible");
        when(delegado.getConnection()).thenReturn(conexion);
        when(conexion.prepareStatement("SELECT set_config('app.tenant_id', ?, false)"))
                .thenThrow(causa);
        TenantContext.fijar(8L);

        SQLException lanzada = assertThrows(SQLException.class,
                () -> new TenantDataSource(delegado).getConnection());

        assertSame(causa, lanzada);
        verify(conexion).close();
    }
}
