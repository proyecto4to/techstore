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
        assertEquals(56, total, "Cambió la cantidad de migraciones; actualizá este número a propósito");
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
                "audit_log", "intento_login", "autonumerador", "sesiones_refresh", "favoritos",
                "dispositivos_moviles", "notification_outbox", "notificaciones_usuario",
                "domain_event_outbox",
                "vehiculos", "usos_vehiculo", "eventos_vehiculo",
                "reparto_asignaciones", "reparto_historial", "cobros_repartidor",
                "movimientos_caja_repartidor", "cierres_caja_repartidor",
                "transferencias_repartidor_cajero");

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
    @DisplayName("V32 dejó la factura con su propio timbrado y sin el campo tipo duplicado")
    void facturaConDatosFiscalesPropios() throws SQLException {
        // El timbrado admite nulo a propósito: las facturas anteriores a esta
        // migración pueden no tener con qué rellenarlo, y un nulo dice la verdad
        // mejor que un cero inventado.
        assertTrue(existeColumna("facturas", "timbrado"));
        assertEquals("YES", nullable("facturas", "timbrado"));

        assertTrue(existeColumna("facturas", "condicion_venta"));
        assertEquals("NO", nullable("facturas", "condicion_venta"));
        assertEquals("NO", nullable("facturas", "receptor_tipo_documento"));

        // `tipo` guardaba lo mismo que `condicion_venta`: tener los dos garantiza
        // que algún día se contradigan.
        assertFalse(existeColumna("facturas", "tipo"),
                "La columna tipo quedó viva y duplica a condicion_venta");
    }

    @Test
    @DisplayName("V32 creó el comprobante del proveedor con sus reglas en la base")
    void comprobanteDelProveedor() throws SQLException {
        assertTrue(contar("SELECT count(*) FROM information_schema.tables "
                + "WHERE table_schema = 'public' AND table_name = 'compra_comprobantes'") == 1);

        assertEquals("NO", nullable("compra_comprobantes", "fecha_emision"));
        assertEquals("NO", nullable("compra_comprobantes", "total"));
        // Timbrado y número admiten nulo: el despacho de importación no lleva
        // timbrado y el ticket de máquina registradora no lleva número.
        assertEquals("YES", nullable("compra_comprobantes", "timbrado"));
        assertEquals("YES", nullable("compra_comprobantes", "numero"));

        // Cargar dos veces la misma factura del proveedor duplicaría el crédito
        // fiscal: lo impide la base, no solo el servicio.
        assertTrue(existeRestriccion("comprobante_proveedor_unico"));
        assertTrue(existeRestriccion("comprobante_por_compra"));
        assertTrue(existeRestriccion("comprobante_desglose_cuadra"));
        assertTrue(existeRestriccion("comprobante_importes_positivos"));
    }

    @Test
    @DisplayName("V33 dejó el envío del pedido al proveedor, y no como estado del pedido")
    void envioDelPedidoAlProveedor() throws SQLException {
        assertTrue(existeColumna("pedidos", "enviado_en"));
        assertTrue(existeColumna("pedidos", "enviado_canal"));
        // Ambas admiten nulo: la enorme mayoría de los pedidos son ventas, que
        // no se le mandan a ningún proveedor.
        assertEquals("YES", nullable("pedidos", "enviado_en"));
        assertEquals("YES", nullable("pedidos", "enviado_canal"));

        // Que el pedido se haya mandado no es que el proveedor lo haya aceptado:
        // el estado sigue siendo el de siempre.
        assertEquals(3, contar("SELECT count(*) FROM pg_enum e JOIN pg_type t ON t.oid = e.enumtypid "
                + "WHERE t.typname = 'canal_envio'"),
                "El canal tiene que admitir correo, WhatsApp y ambos");
    }

    @Test
    @DisplayName("la tabla nueva quedó aislada por empresa como el resto del dominio")
    void elComprobanteTambienTieneRls() throws SQLException {
        assertEquals(1, contar("SELECT count(*) FROM pg_class "
                + "WHERE relname = 'compra_comprobantes' AND relrowsecurity AND relforcerowsecurity"),
                "Sin RLS forzado, una empresa vería los comprobantes de otra");
        assertEquals(2, contar("SELECT count(*) FROM pg_policies "
                + "WHERE tablename = 'compra_comprobantes'"),
                "Faltan las políticas de aislamiento y de mantenimiento");
    }

    @Test
    @DisplayName("V35 guarda sesiones refresh revocables sin token en claro y con RLS")
    void sesionesRefreshSeguras() throws SQLException {
        for (String columna : List.of("tenant_id", "usuario_id", "token_id", "token_hash",
                "creado_en", "expira_en", "revocado_en", "reemplazado_por")) {
            assertTrue(existeColumna("sesiones_refresh", columna), "Falta " + columna);
        }
        assertEquals(1, contar("SELECT count(*) FROM pg_constraint "
                + "WHERE conname = 'sesion_refresh_token_unico'"));
        assertEquals(1, contar("SELECT count(*) FROM pg_constraint "
                + "WHERE conname = 'sesion_refresh_hash_unico'"));
        assertEquals(1, contar("SELECT count(*) FROM pg_class "
                + "WHERE relname = 'sesiones_refresh' AND relrowsecurity AND relforcerowsecurity"));
        assertEquals(2, contar("SELECT count(*) FROM pg_policies "
                + "WHERE tablename = 'sesiones_refresh'"));
    }

    @Test
    @DisplayName("V36 creó favoritos únicos por usuario y aislados por tenant")
    void favoritosSeguros() throws SQLException {
        for (String columna : List.of("tenant_id", "usuario_id", "producto_id", "creado_en")) {
            assertTrue(existeColumna("favoritos", columna), "Falta " + columna);
        }
        assertEquals(1, contar("SELECT count(*) FROM pg_constraint "
                + "WHERE conname = 'favorito_usuario_producto_unico'"));
        assertEquals(1, contar("SELECT count(*) FROM pg_class "
                + "WHERE relname = 'favoritos' AND relrowsecurity AND relforcerowsecurity"));
        assertEquals(2, contar("SELECT count(*) FROM pg_policies WHERE tablename = 'favoritos'"));
    }

    @Test
    @DisplayName("V41 creó el outbox durable, deduplicado y aislado por tenant")
    void notificationOutboxSeguro() throws SQLException {
        for (String columna : List.of("tenant_id", "event_type", "consumer_channel",
                "aggregate_type", "aggregate_id", "recipient_user_id", "payload", "status",
                "attempts", "next_attempt_at", "claimed_at", "processed_at", "created_at",
                "last_error_sanitized", "idempotency_key")) {
            assertTrue(existeColumna("notification_outbox", columna), "Falta " + columna);
        }
        assertEquals("jsonb", tipo("notification_outbox", "payload"));
        assertEquals(1, contar("SELECT count(*) FROM pg_constraint "
                + "WHERE conname = 'uk_notification_outbox_idempotency'"));
        assertEquals(1, contar("SELECT count(*) FROM pg_class "
                + "WHERE relname = 'notification_outbox' AND relrowsecurity AND relforcerowsecurity"));
        assertEquals(2, contar("SELECT count(*) FROM pg_policies "
                + "WHERE tablename = 'notification_outbox'"));
        assertEquals(3, contar("SELECT count(*) FROM pg_indexes "
                + "WHERE tablename = 'notification_outbox' AND indexname LIKE 'idx_notification_outbox_%'"));
    }

    @Test
    @DisplayName("V42 creó el centro de notificaciones idempotente y aislado por tenant")
    void centroNotificacionesSeguro() throws SQLException {
        for (String columna : List.of("tenant_id", "usuario_id", "tipo", "titulo", "mensaje",
                "referencia_tipo", "referencia_id", "deep_link", "leida", "leida_en",
                "source_outbox_id", "created_at")) {
            assertTrue(existeColumna("notificaciones_usuario", columna), "Falta " + columna);
        }
        assertEquals(1, contar("SELECT count(*) FROM pg_constraint "
                + "WHERE conname = 'uk_notificacion_source'"));
        assertEquals(1, contar("SELECT count(*) FROM pg_class "
                + "WHERE relname = 'notificaciones_usuario' AND relrowsecurity AND relforcerowsecurity"));
        assertEquals(2, contar("SELECT count(*) FROM pg_policies "
                + "WHERE tablename = 'notificaciones_usuario'"));
        assertEquals(2, contar("SELECT count(*) FROM pg_indexes "
                + "WHERE tablename = 'notificaciones_usuario' AND indexname LIKE 'idx_notificaciones_usuario_%'"));
        assertEquals(1, contar("SELECT count(*) FROM pg_constraint "
                + "WHERE conname = 'ck_notification_outbox_channel' "
                + "AND pg_get_constraintdef(oid) LIKE '%INTERNAL%'"));
    }

    @Test
    @DisplayName("V43 creó eventos transaccionales, recuperables y aislados por tenant")
    void domainEventOutboxSeguro() throws SQLException {
        for (String columna : List.of("tenant_id", "event_type", "aggregate_type",
                "aggregate_id", "payload", "status", "attempts", "next_attempt_at",
                "claimed_at", "processed_at", "occurred_at", "last_error")) {
            assertTrue(existeColumna("domain_event_outbox", columna), "Falta " + columna);
        }
        assertEquals("jsonb", tipo("domain_event_outbox", "payload"));
        assertEquals(1, contar("SELECT count(*) FROM pg_class WHERE relname = 'domain_event_outbox' "
                + "AND relrowsecurity AND relforcerowsecurity"));
        assertEquals(2, contar("SELECT count(*) FROM pg_policies WHERE tablename = 'domain_event_outbox'"));
        assertEquals(21, contar("SELECT count(*) FROM pg_trigger WHERE tgname LIKE 'trg_sync_%' "
                + "AND NOT tgisinternal"));
    }

    @Test
    @DisplayName("V44 guarda solo hashes de recuperación con aislamiento tenant")
    void passwordRecoverySeguro() throws SQLException {
        for (String columna : java.util.List.of(
                "tenant_id", "usuario_id", "token_hash", "creado_en", "expira_en", "usado_en")) {
            assertTrue(existeColumna("password_reset_tokens", columna), "Falta " + columna);
        }
        assertEquals(2, contar("SELECT count(*) FROM pg_policies WHERE tablename = 'password_reset_tokens'"));
        assertEquals(1, contar("SELECT count(*) FROM pg_indexes WHERE indexname = 'idx_password_reset_user_active'"));
    }

    @Test
    @DisplayName("V45 agrega metadatos del archivo privado del comprobante de compra")
    void archivoComprobanteCompra() throws SQLException {
        assertTrue(existeColumna("compra_comprobantes", "archivo_nombre"));
        assertTrue(existeColumna("compra_comprobantes", "archivo_content_type"));
    }

    @Test
    @DisplayName("V46 deja el rol Repartidor y los candados de reparto, caja y flota")
    void repartidoresVehiculosYCaja() throws SQLException {
        assertEquals(1, contar("SELECT count(*) FROM roles WHERE nombre = 'Repartidor'"));
        // Los índices parciales son lo que impide que la plata y las entregas se
        // dupliquen: un pedido no puede estar en dos manos a la vez, un repartidor
        // no puede tener dos vehículos en uso, y no se abre un cierre sobre otro
        // todavía pendiente. Si una migración futura los pisa, esto avisa.
        assertEquals(1, contar("SELECT count(*) FROM pg_indexes WHERE indexname = 'uk_reparto_pedido_activo'"));
        assertEquals(1, contar("SELECT count(*) FROM pg_indexes WHERE indexname = 'uk_uso_repartidor_activo'"));
        assertEquals(1, contar("SELECT count(*) FROM pg_indexes WHERE indexname = 'uk_cierre_rep_pendiente'"));
        assertEquals(1, contar("SELECT count(*) FROM pg_indexes WHERE indexname = 'uk_mov_caja_rep_referencia'"));
    }

    @Test
    @DisplayName("los datos semilla quedaron cargados")
    void datosSemilla() throws SQLException {
        assertEquals(4, contar("SELECT count(*) FROM roles"));
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

    private String tipo(String tabla, String columna) throws SQLException {
        try (Connection con = dataSource.getConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT data_type FROM information_schema.columns WHERE table_name = '"
                             + tabla + "' AND column_name = '" + columna + "'")) {
            assertTrue(rs.next(), "No existe la columna " + tabla + "." + columna);
            return rs.getString(1);
        }
    }
}
