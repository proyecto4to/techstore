package com.techstore.integracion;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lo que ningún test unitario podía comprobar: que las migraciones corran de
 * verdad, en orden, sobre una base vacía, y que el esquema que dejan coincida
 * con lo que las entidades JPA esperan.
 *
 * <p>Buena parte de la verificación es implícita y silenciosa: el perfil de
 * pruebas usa {@code ddl-auto=validate}, así que si una entidad y su tabla se
 * desincronizan —una columna renombrada, un tipo cambiado— el contexto de Spring
 * ni siquiera arranca y todos los tests de esta clase fallan.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Migraciones sobre PostgreSQL real")
class MigracionesIT {

    @DynamicPropertySource
    static void base(DynamicPropertyRegistry registro) {
        BaseDatosDePrueba.registrar(registro);
    }

    @Autowired
    private DataSource dataSource;

    @Test
    @DisplayName("todas se aplicaron y ninguna quedó fallida")
    void migracionesAplicadas() throws SQLException {
        List<String> fallidas = new ArrayList<>();
        int total = 0;
        try (Connection con = dataSource.getConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT version, description, success FROM flyway_schema_history "
                             + "WHERE version IS NOT NULL ORDER BY installed_rank")) {
            while (rs.next()) {
                total++;
                if (!rs.getBoolean("success")) {
                    fallidas.add(rs.getString("version") + " " + rs.getString("description"));
                }
            }
        }
        assertTrue(fallidas.isEmpty(), "Migraciones fallidas: " + fallidas);
        assertEquals(28, total, "Cambió la cantidad de migraciones; actualizá este número a propósito");
    }

    @Test
    @DisplayName("el esquema tiene las tablas del dominio")
    void tablasDelDominio() throws SQLException {
        List<String> esperadas = List.of(
                "usuarios", "roles", "clientes", "productos", "marcas", "modelos",
                "pedidos", "pedido_detalles", "facturas", "pagos",
                "movimiento_caja", "cierres_caja", "aperturas_caja",
                "notas_credito", "nota_credito_detalles",
                "proveedores", "configuracion", "correos",
                "audit_log", "intento_login", "autonumerador");

        List<String> presentes = new ArrayList<>();
        try (Connection con = dataSource.getConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'")) {
            while (rs.next()) {
                presentes.add(rs.getString(1));
            }
        }
        List<String> faltantes = esperadas.stream().filter(t -> !presentes.contains(t)).toList();
        assertTrue(faltantes.isEmpty(), "Faltan tablas: " + faltantes);
    }

    @Test
    @DisplayName("las columnas agregadas por V19-V22 quedaron como corresponde")
    void columnasRecientes() throws SQLException {
        // stock_minimo: NOT NULL con default 0, para que los productos que ya
        // existían no empiecen a avisar solos.
        assertEquals("NO", nullable("productos", "stock_minimo"));
        // usuario_id de la auditoría: al revés, tiene que admitir nulo, porque
        // hay acciones sin sesión detrás.
        assertEquals("YES", nullable("audit_log", "usuario_id"));
        assertEquals("YES", nullable("audit_log", "entidad_id"));
        assertTrue(existeColumna("audit_log", "usuario_email"));
        assertTrue(existeColumna("intento_login", "bloqueado_hasta"));
    }

    @Test
    @DisplayName("V25 dejó la moneda en cada documento con dinero")
    void monedaPorDocumento() throws SQLException {
        // Las tablas que guardan importes llevan su moneda; las de detalle no,
        // porque heredan la del documento padre.
        List<String> conMoneda = List.of("productos", "pedidos", "facturas", "notas_credito",
                "pagos", "movimiento_caja", "cierres_caja", "aperturas_caja");
        for (String tabla : conMoneda) {
            assertTrue(existeColumna(tabla, "moneda"), "Falta la columna moneda en " + tabla);
            assertEquals("NO", nullable(tabla, "moneda"), "La moneda de " + tabla + " admite nulo");
        }
        assertFalse(existeColumna("pedido_detalles", "moneda"),
                "Un detalle no tiene moneda propia: es la de su pedido");
        assertFalse(existeColumna("nota_credito_detalles", "moneda"),
                "Un detalle no tiene moneda propia: es la de su nota de crédito");

        // Todo lo que ya existía es en guaraníes.
        assertEquals(0, contar("SELECT count(*) FROM productos WHERE moneda <> 'PYG'"),
                "Quedaron productos en una moneda que no es la del backfill");
    }

    @Test
    @DisplayName("V26 dejó el descuento por ítem, y la base rechaza los imposibles")
    void descuentoPorItem() throws SQLException {
        for (String tabla : List.of("pedido_detalles", "nota_credito_detalles")) {
            assertTrue(existeColumna(tabla, "descuento"), "Falta la columna descuento en " + tabla);
            assertEquals("NO", nullable(tabla, "descuento"), "El descuento de " + tabla + " admite nulo");
        }
        // El descuento se guarda por unidad, así que no puede superar el precio
        // unitario ni ser negativo. Lo garantiza la base, no solo el código.
        assertTrue(existeRestriccion("pedido_detalles_descuento_valido"),
                "Sin el CHECK, un UPDATE a mano podría dejar una línea de importe negativo");
        assertTrue(existeRestriccion("nota_credito_detalles_descuento_valido"));
    }

    /** {@code true} si la base tiene una restricción con ese nombre. */
    private boolean existeRestriccion(String nombre) throws SQLException {
        return contar("SELECT count(*) FROM pg_constraint WHERE conname = '" + nombre + "'") == 1;
    }

    @Test
    @DisplayName("los datos semilla quedaron cargados")
    void datosSemilla() throws SQLException {
        assertEquals(3, contar("SELECT count(*) FROM roles"));
        assertEquals(1, contar("SELECT count(*) FROM usuarios WHERE email = 'admin@techstore.py'"));
        assertTrue(contar("SELECT count(*) FROM productos") > 0, "El catálogo semilla quedó vacío");
    }

    @Test
    @DisplayName("la referencia de caja quedó desambiguada por V22")
    void referenciasSinAmbiguedad() throws SQLException {
        // Sobre una base recién migrada no hay movimientos, así que lo que se
        // comprueba es que la migración corra y no deje nada con el formato viejo.
        assertEquals(0,
                contar("SELECT count(*) FROM movimiento_caja "
                        + "WHERE concepto LIKE 'Cobro pedido%' AND referencia NOT LIKE 'FAC %'"),
                "Quedaron cobros con la referencia sin prefijo");
    }

    private long contar(String sql) throws SQLException {
        try (Connection con = dataSource.getConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private String nullable(String tabla, String columna) throws SQLException {
        try (Connection con = dataSource.getConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT is_nullable FROM information_schema.columns WHERE table_name = '"
                             + tabla + "' AND column_name = '" + columna + "'")) {
            assertTrue(rs.next(), "No existe la columna " + tabla + "." + columna);
            return rs.getString(1);
        }
    }

    private boolean existeColumna(String tabla, String columna) throws SQLException {
        return contar("SELECT count(*) FROM information_schema.columns WHERE table_name = '"
                + tabla + "' AND column_name = '" + columna + "'") == 1;
    }
}
