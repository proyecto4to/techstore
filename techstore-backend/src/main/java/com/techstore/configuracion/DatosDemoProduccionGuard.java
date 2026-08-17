package com.techstore.configuracion;

import com.techstore.comun.tenant.TenantContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

import java.util.Locale;

/**
 * Evita que un despliegue comercial nazca con el catálogo y los proveedores
 * ficticios que acompañan al entorno de desarrollo.
 *
 * <p>La migración V34 marca únicamente las filas históricas conocidas. El
 * perfil {@code prod} falla cerrado por defecto. Para provisionar una base
 * comercial nueva se debe elegir conscientemente {@code purge}; el perfil de
 * desarrollo nunca ejecuta este runner y conserva intacta la base local.
 */
@Component
@Profile("prod")
@Order(10)
@Slf4j
public class DatosDemoProduccionGuard implements ApplicationRunner {

    static final String REFUSE = "refuse";
    static final String PURGE = "purge";

    private final JdbcOperations jdbc;
    private final TransactionOperations transactions;
    private final String policy;

    public DatosDemoProduccionGuard(
            JdbcOperations jdbc,
            TransactionOperations transactions,
            @Value("${app.demo-data-policy:refuse}") String policy) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.policy = normalizar(policy);
    }

    @Override
    public void run(ApplicationArguments args) {
        TenantContext.fijar(TenantContext.TENANT_POR_DEFECTO);
        try {
            transactions.executeWithoutResult(estado -> verificarOProvisionar());
        } finally {
            TenantContext.limpiar();
        }
    }

    private void verificarOProvisionar() {
        long encontrados = contarDatosDemo();
        if (encontrados == 0) {
            return;
        }
        if (REFUSE.equals(policy)) {
            throw new IllegalStateException(
                    "El perfil prod detectó datos de demostración (" + encontrados + "). "
                            + "Provisiona una base nueva con APP_DEMO_DATA_POLICY=purge o revisa los datos manualmente.");
        }

        // Las ofertas no son información contable y apuntan a ambos catálogos.
        jdbc.update("""
                DELETE FROM proveedor_productos pp
                WHERE pp.tenant_id = ?
                  AND (EXISTS (SELECT 1 FROM productos p
                               WHERE p.id = pp.producto_id AND p.es_demo)
                    OR EXISTS (SELECT 1 FROM proveedores pr
                               WHERE pr.id = pp.proveedor_id AND pr.es_demo))
                """, TenantContext.actual());

        // En una base nueva se eliminan. Si ya forman parte de documentos
        // históricos se conservan desactivados, sin stock y sin aparecer en la
        // tienda, para no romper la trazabilidad contable.
        int productosEliminados = jdbc.update("""
                DELETE FROM productos p
                WHERE p.tenant_id = ? AND p.es_demo
                  AND NOT EXISTS (SELECT 1 FROM pedido_detalles d WHERE d.producto_id = p.id)
                  AND NOT EXISTS (SELECT 1 FROM nota_credito_detalles d WHERE d.producto_id = p.id)
                """, TenantContext.actual());
        int productosArchivados = jdbc.update("""
                UPDATE productos
                SET activo = FALSE,
                    stock_real = 0,
                    stock_comprometido = 0,
                    es_demo = FALSE
                WHERE tenant_id = ? AND es_demo
                """, TenantContext.actual());

        int proveedoresEliminados = jdbc.update("""
                DELETE FROM proveedores p
                WHERE p.tenant_id = ? AND p.es_demo
                  AND NOT EXISTS (SELECT 1 FROM pedidos pe WHERE pe.proveedor_id = p.id)
                """, TenantContext.actual());
        int proveedoresArchivados = jdbc.update("""
                UPDATE proveedores
                SET activo = FALSE, es_demo = FALSE
                WHERE tenant_id = ? AND es_demo
                """, TenantContext.actual());

        int marcasEliminadas = jdbc.update("""
                DELETE FROM marcas m
                WHERE m.tenant_id = ? AND m.es_demo
                  AND NOT EXISTS (SELECT 1 FROM productos p WHERE p.marca_id = m.id)
                  AND NOT EXISTS (SELECT 1 FROM modelos mo WHERE mo.marca_id = m.id)
                """, TenantContext.actual());
        int marcasConservadas = jdbc.update("""
                UPDATE marcas SET es_demo = FALSE
                WHERE tenant_id = ? AND es_demo
                """, TenantContext.actual());

        log.info(
                "Provisionamiento comercial completado: productos eliminados={}, archivados={}; "
                        + "proveedores eliminados={}, archivados={}; marcas eliminadas={}, conservadas={}",
                productosEliminados, productosArchivados,
                proveedoresEliminados, proveedoresArchivados,
                marcasEliminadas, marcasConservadas);
    }

    private long contarDatosDemo() {
        Long total = jdbc.queryForObject("""
                SELECT
                    (SELECT COUNT(*) FROM productos WHERE es_demo) +
                    (SELECT COUNT(*) FROM proveedores WHERE es_demo) +
                    (SELECT COUNT(*) FROM marcas WHERE es_demo)
                """, Long.class);
        return total == null ? 0 : total;
    }

    private static String normalizar(String valor) {
        String normalizado = valor == null ? "" : valor.trim().toLowerCase(Locale.ROOT);
        if (!REFUSE.equals(normalizado) && !PURGE.equals(normalizado)) {
            throw new IllegalArgumentException(
                    "APP_DEMO_DATA_POLICY debe ser 'refuse' o 'purge'; valor recibido: " + normalizado);
        }
        return normalizado;
    }
}
