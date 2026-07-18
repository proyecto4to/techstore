package com.techstore.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Datos fiscales de la empresa que aparecen en la factura (formato DNIT).
 * Reemplazar por los datos reales del contribuyente antes de facturar en serio.
 */
@ConfigurationProperties(prefix = "empresa")
public record EmpresaProperties(
        String nombre,
        String ruc,
        String direccion,
        String ciudad,
        String telefono,
        String actividad,
        String timbrado,
        String timbradoVigencia) {
}
