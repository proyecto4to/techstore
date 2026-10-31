package com.techstore.ventas;

import com.techstore.comun.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Borra, una vez por día, los comprobantes que se subieron para una compra que
 * nunca se confirmó.
 *
 * <p>Va empresa por empresa por lo mismo que el outbox: las políticas de Row
 * Level Security exigen que la conexión diga de qué empresa es, y una tarea
 * programada no hereda ese dato de ninguna petición. Sin fijarla, la consulta
 * no ve ninguna fila y la limpieza no borra nada, sin error.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LimpiezaDeComprobantesSueltos {

    private final JdbcTemplate jdbcTemplate;
    private final PagoComprobanteService comprobanteService;

    @Scheduled(cron = "${pagos.comprobantes.limpieza-cron:0 20 4 * * *}")
    public void limpiar() {
        try {
            for (Long tenantId : jdbcTemplate.queryForList(
                    "SELECT id FROM tenants WHERE activo = TRUE ORDER BY id", Long.class)) {
                int borrados = TenantContext.comoTenant(tenantId, comprobanteService::limpiarSueltos);
                if (borrados > 0) {
                    log.info("Se borraron {} comprobantes sin checkout de la empresa {}", borrados, tenantId);
                }
            }
        } catch (RuntimeException error) {
            log.warn("No se pudieron limpiar los comprobantes sin checkout: {}", error.getMessage());
        }
    }
}
