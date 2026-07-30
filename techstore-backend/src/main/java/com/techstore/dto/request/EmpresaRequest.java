package com.techstore.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Datos del emisor que salen impresos en la factura y viajan en el Documento
 * Electrónico. Los campos que se manden vacíos vuelven al valor de
 * application.properties.
 */
public record EmpresaRequest(
        @NotBlank(message = "El nombre o razón social es obligatorio") @Size(max = 255) String nombre,
        @NotBlank(message = "El RUC es obligatorio") @Size(max = 20) String ruc,
        @Size(max = 255) String direccion,
        @Size(max = 100) String ciudad,
        @Size(max = 50) String telefono,
        @Size(max = 255) String actividad,
        @Size(max = 20) String timbrado,
        @Size(max = 100) String timbradoVigencia,
        @Pattern(regexp = "|\\d{4}-\\d{2}-\\d{2}",
                message = "La fecha de inicio del timbrado va en formato AAAA-MM-DD")
        String timbradoInicioVigencia,
        @Min(value = 1, message = "1 = persona física, 2 = persona jurídica")
        @Max(value = 2, message = "1 = persona física, 2 = persona jurídica")
        Integer tipoContribuyente,
        @Pattern(regexp = "|\\d{3}", message = "El establecimiento son 3 dígitos (ej. 001)")
        String establecimiento,
        @Pattern(regexp = "|\\d{3}", message = "El punto de expedición son 3 dígitos (ej. 001)")
        String puntoExpedicion,
        @Size(max = 10) String actividadCodigo,
        @Size(max = 5) String departamentoCodigo,
        @Size(max = 100) String departamentoNombre,
        @Size(max = 5) String distritoCodigo,
        @Size(max = 100) String distritoNombre,
        @Size(max = 5) String ciudadCodigo,
        @Size(max = 100) String ciudadNombre) {
}
