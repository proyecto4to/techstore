package com.techstore.empresa;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Datos fiscales de la empresa que aparecen en la factura (formato DNIT)
 * y en el Documento Electrónico del SIFEN. Reemplazar por los datos reales
 * del contribuyente antes de facturar en serio.
 */
@ConfigurationProperties(prefix = "empresa")
public record EmpresaProperties(
        String nombre,
        String ruc,
        String direccion,
        String ciudad,
        String telefono,
        /** Correo del emisor. El KuDE lo lista entre los datos de la cabecera. */
        String email,
        String actividad,
        String timbrado,
        String timbradoVigencia,
        // Datos adicionales para el Documento Electrónico del SIFEN.
        Integer tipoContribuyente,   // 1 = persona física, 2 = persona jurídica
        String establecimiento,      // 3 dígitos (ej. 001)
        String puntoExpedicion,      // 3 dígitos (ej. 001)
        String actividadCodigo,      // código de actividad económica (Tabla 3)
        String departamentoCodigo,
        String departamentoNombre,
        String distritoCodigo,
        String distritoNombre,
        String ciudadCodigo,
        String ciudadNombre,
        String timbradoInicioVigencia,
        /**
         * Qué número declarar como identificación del comprador en las ventas a
         * consumidor final.
         *
         * <p>El Registro de Comprobantes admite el tipo de identificación 15
         * ("sin nombre") justamente para esas ventas —y entonces no pide el
         * nombre—, pero el campo del número igual es obligatorio. Qué se pone
         * ahí lo define el contador de cada empresa, así que es configurable y
         * no una constante escondida en el código.
         */
        String identificacionSinNombre) {

    /** RUC sin el dígito verificador (parte antes del guion). */
    public String rucNumero() {
        return ruc.contains("-") ? ruc.substring(0, ruc.indexOf('-')) : ruc;
    }

    /** Dígito verificador del RUC (parte después del guion). */
    public int rucDv() {
        return ruc.contains("-") ? Integer.parseInt(ruc.substring(ruc.indexOf('-') + 1)) : 0;
    }
}
