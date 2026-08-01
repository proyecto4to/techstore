package com.techstore.comun.tenant;

import org.hibernate.cfg.AvailableSettings;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Le dice a Hibernate de qué empresa es la operación en curso.
 *
 * <p>Con esto conectado, los campos anotados con {@code @TenantId} dejan de ser
 * responsabilidad de quien escribe la consulta: Hibernate agrega solo el
 * {@code tenant_id} en cada INSERT y el {@code WHERE tenant_id = ?} en cada
 * SELECT. Es lo que evita que el aislamiento dependa de que nadie se olvide
 * nunca de filtrar.
 */
@Component
public class ResolvedorDeTenant implements CurrentTenantIdentifierResolver<Long>,
        HibernatePropertiesCustomizer {

    @Override
    public Long resolveCurrentTenantIdentifier() {
        return TenantContext.actual();
    }

    /**
     * Hibernate cachea entidades por sesión. Al validar que el tenant siga
     * siendo el mismo, se asegura de no servir desde la caché una fila que se
     * cargó atendiendo a otra empresa.
     */
    @Override
    public boolean validateExistingCurrentSessions() {
        return true;
    }

    /**
     * El identificador de tenant es un {@code Long} y no un {@code String}, que
     * es lo que Hibernate asume por omisión.
     */
    @Override
    public boolean isRoot(Long tenantId) {
        return false;
    }

    @Override
    public void customize(Map<String, Object> propiedades) {
        propiedades.put(AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, this);
    }
}
