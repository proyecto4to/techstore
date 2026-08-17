package com.techstore.comun.tenant;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.datasource.DelegatingDataSource;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.PreparedStatement;
import java.sql.Statement;
import javax.sql.DataSource;

/**
 * Le cuenta a PostgreSQL de qué empresa es cada conexión, para que las políticas
 * de Row Level Security puedan aplicarse.
 *
 * <p>El valor se fija en cada préstamo de conexión, no una sola vez: el pool
 * reutiliza conexiones entre peticiones, y una que quedara marcada con la
 * empresa anterior le mostraría a la siguiente los datos de otra. Por eso cada
 * checkout lo reescribe, y cuando no hay empresa explícita lo borra.
 *
 * <p>Sin empresa fijada la variable queda vacía, {@code tenant_actual()} devuelve
 * NULL y las políticas no dejan ver nada. Es intencional: algo que separa los
 * datos de dos clientes tiene que fallar cerrado. La única excepción es el dueño
 * de las tablas, que necesita ver todo para migrar; de eso se ocupa la política
 * de mantenimiento de V24.
 */
@Slf4j
public class TenantDataSource extends DelegatingDataSource {

    public TenantDataSource(DataSource delegado) {
        super(delegado);
    }

    @Override
    public Connection getConnection() throws SQLException {
        return marcar(super.getConnection());
    }

    @Override
    public Connection getConnection(String usuario, String password) throws SQLException {
        return marcar(super.getConnection(usuario, password));
    }

    private Connection marcar(Connection con) throws SQLException {
        try {
            if (TenantContext.hayTenant()) {
                try (PreparedStatement st = con.prepareStatement(
                        "SELECT set_config('app.tenant_id', ?, false)")) {
                    st.setString(1, TenantContext.actual().toString());
                    st.execute();
                }
            } else {
                try (Statement st = con.createStatement()) {
                    st.execute("RESET app.tenant_id");
                }
            }
        } catch (SQLException ex) {
            // El aislamiento es una barrera de seguridad: si no se puede fijar,
            // esta conexión no debe llegar a ningún repositorio.
            try {
                con.close();
            } catch (SQLException alCerrar) {
                ex.addSuppressed(alCerrar);
            }
            log.error("No se pudo fijar app.tenant_id; se descartó la conexión", ex);
            throw ex;
        }
        return con;
    }
}
