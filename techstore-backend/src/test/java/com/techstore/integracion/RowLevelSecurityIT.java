package com.techstore.integracion;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Que el aislamiento entre empresas lo haga cumplir <b>la base</b>, y no solo
 * Hibernate.
 *
 * <p>Importa la diferencia: Hibernate protege lo que pasa por Hibernate. Una
 * consulta nativa, un script de mantenimiento o un bug futuro pueden esquivarlo.
 * Row Level Security no se esquiva desde la aplicación.
 *
 * <p>El test conecta con un rol <b>sin privilegios de administrador</b> creado a
 * propósito, porque las políticas no se le aplican ni a superusuarios ni a roles
 * con BYPASSRLS. Con el rol equivocado este test pasaría siempre y no probaría
 * nada — que es exactamente lo que pasaba antes de separar el rol de la
 * aplicación del de administración.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Row Level Security en la base")
class RowLevelSecurityIT {

    private static final String ROL = "app_sin_privilegios";
    private static final String CLAVE = "prueba-rls";
    private static final String PRODUCTO_TENANT_1 = "Producto RLS empresa 1";
    private static final String PRODUCTO_TENANT_2 = "Producto RLS empresa 2";

    @DynamicPropertySource
    static void base(DynamicPropertyRegistry registro) {
        BaseDatosDePrueba.registrar(registro);
    }

    @Autowired
    private DataSource dataSource;

    private String urlJdbc;

    @BeforeEach
    void prepararRolLimitado() throws SQLException {
        try (Connection con = dataSource.getConnection(); Statement st = con.createStatement()) {
            // La URL de EmbeddedPostgres trae ?user=postgres. Si se reutiliza
            // tal cual, ese parámetro pisa el usuario entregado a DriverManager
            // y el test termina conectando como superusuario sin advertirlo.
            // Se quitan los parámetros para probar realmente con el rol limitado.
            urlJdbc = con.getMetaData().getURL().split("\\?", 2)[0];
            st.execute("""
                    DO $$
                    BEGIN
                        IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '%s') THEN
                            CREATE ROLE %s LOGIN PASSWORD '%s';
                        END IF;
                    END $$
                    """.formatted(ROL, ROL, CLAVE));
            st.execute("ALTER ROLE " + ROL + " NOSUPERUSER NOBYPASSRLS");
            st.execute("GRANT USAGE ON SCHEMA public TO " + ROL);
            st.execute("GRANT SELECT ON ALL TABLES IN SCHEMA public TO " + ROL);

            st.execute("INSERT INTO tenants (id, codigo, nombre) VALUES (2, 'rls-otra', 'Otra') "
                    + "ON CONFLICT (id) DO NOTHING");
            st.execute("DELETE FROM productos WHERE nombre IN ('" + PRODUCTO_TENANT_1
                    + "', '" + PRODUCTO_TENANT_2 + "')");
            st.execute("INSERT INTO productos (tenant_id, nombre, precio, stock_real, stock_comprometido, "
                    + "stock_minimo, activo) VALUES "
                    + "(1, '" + PRODUCTO_TENANT_1 + "', 1000, 1, 0, 0, true), "
                    + "(2, '" + PRODUCTO_TENANT_2 + "', 1000, 1, 0, 0, true)");
        }
    }

    private long productosVisibles(String tenantId) throws SQLException {
        try (Connection con = DriverManager.getConnection(urlJdbc, ROL, CLAVE);
             Statement st = con.createStatement()) {
            try (ResultSet identidad = st.executeQuery("SELECT current_user")) {
                identidad.next();
                assertEquals(ROL, identidad.getString(1),
                        "La prueba de RLS no está usando el rol limitado");
            }
            if (tenantId != null) {
                st.execute("SET app.tenant_id = '" + tenantId + "'");
            }
            try (ResultSet rs = st.executeQuery("SELECT count(*) FROM productos")) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private boolean productoVisible(String tenantId, String nombre) throws SQLException {
        try (Connection con = DriverManager.getConnection(urlJdbc, ROL, CLAVE);
             Statement tenant = con.createStatement()) {
            tenant.execute("SET app.tenant_id = '" + tenantId + "'");
            try (PreparedStatement consulta = con.prepareStatement(
                    "SELECT count(*) FROM productos WHERE nombre = ?")) {
                consulta.setString(1, nombre);
                try (ResultSet rs = consulta.executeQuery()) {
                    rs.next();
                    return rs.getLong(1) == 1;
                }
            }
        }
    }

    @Test
    @DisplayName("sin empresa fijada no se ve ninguna fila")
    void fallaCerrado() throws SQLException {
        assertEquals(0, productosVisibles(null),
                "Sin app.tenant_id la base deja ver filas: el aislamiento no está fallando cerrado");
    }

    @Test
    @DisplayName("con una empresa fijada se ve esa y solo esa")
    void soloLaEmpresaFijada() throws SQLException {
        // La base es compartida por toda la suite, así que no se afirma una
        // cantidad total frágil. Se usan dos marcadores inequívocos y se
        // comprueba en ambos sentidos que cada tenant ve el suyo, nunca el otro.
        assertTrue(productoVisible("1", PRODUCTO_TENANT_1),
                "La empresa 1 no ve su propio producto");
        assertFalse(productoVisible("1", PRODUCTO_TENANT_2),
                "La empresa 1 puede ver el producto de la empresa 2");
        assertTrue(productoVisible("2", PRODUCTO_TENANT_2),
                "La empresa 2 no ve su propio producto");
        assertFalse(productoVisible("2", PRODUCTO_TENANT_1),
                "La empresa 2 puede ver el producto de la empresa 1");
    }

    @Test
    @DisplayName("una empresa inexistente no ve nada de las demás")
    void empresaInexistenteNoVeNada() throws SQLException {
        assertEquals(0, productosVisibles("9999"));
    }
}
