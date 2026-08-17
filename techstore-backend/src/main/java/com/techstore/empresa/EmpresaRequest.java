package com.techstore.empresa;

import jakarta.validation.constraints.Email;
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
        @NotBlank(message = "{validacion.empresa.nombre.obligatorio}") @Size(max = 255) String nombre,
        @NotBlank(message = "{validacion.ruc.obligatorio}") @Size(max = 20) String ruc,
        @Size(max = 255) String direccion,
        @Size(max = 100) String ciudad,
        @Size(max = 50) String telefono,
        @Email(message = "{validacion.email.formato}") @Size(max = 255) String email,
        @Size(max = 255) String actividad,
        @Size(max = 20) String timbrado,
        @Size(max = 100) String timbradoVigencia,
        @Pattern(regexp = "|\\d{4}-\\d{2}-\\d{2}",
                message = "{validacion.timbrado.fecha.formato}")
        String timbradoInicioVigencia,
        @Min(value = 1, message = "{validacion.tipo.contribuyente}")
        @Max(value = 2, message = "{validacion.tipo.contribuyente}")
        Integer tipoContribuyente,
        @Pattern(regexp = "|\\d{3}", message = "{validacion.establecimiento.formato}")
        String establecimiento,
        @Pattern(regexp = "|\\d{3}", message = "{validacion.punto.expedicion.formato}")
        String puntoExpedicion,
        @Size(max = 10) String actividadCodigo,
        @Size(max = 5) String departamentoCodigo,
        @Size(max = 100) String departamentoNombre,
        @Size(max = 5) String distritoCodigo,
        @Size(max = 100) String distritoNombre,
        @Size(max = 5) String ciudadCodigo,
        @Size(max = 100) String ciudadNombre,
        /** Qué se declara como identificación del comprador en las ventas a consumidor final. */
        @Size(max = 20) String identificacionSinNombre) {
}
