package com.techstore.tenant;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * Envuelve el {@link DataSource} que arma Spring Boot para que cada conexión
 * salga marcada con la empresa en curso.
 *
 * <p>Se hace con un {@link BeanPostProcessor} y no declarando un DataSource
 * propio para no perder nada de la autoconfiguración —el pool de Hikari, sus
 * tamaños, las métricas del actuator—: se toma el que Boot ya construyó y solo
 * se lo decora.
 */
@Configuration
public class TenantDataSourceConfig {

    @Bean
    public static BeanPostProcessor marcarConexionesConElTenant() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String nombre) {
                if (bean instanceof DataSource ds && !(bean instanceof TenantDataSource)) {
                    return new TenantDataSource(ds);
                }
                return bean;
            }
        };
    }
}
