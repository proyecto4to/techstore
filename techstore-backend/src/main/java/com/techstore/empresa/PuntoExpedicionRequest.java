package com.techstore.empresa;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Alta de una caja o sucursal que emite documentos. */
public record PuntoExpedicionRequest(
        @Pattern(regexp = "\\d{3}", message = "{validacion.establecimiento.formato}")
        String establecimiento,
        @Pattern(regexp = "\\d{3}", message = "{validacion.punto.expedicion.formato}")
        String punto,
        @NotBlank(message = "{validacion.punto.expedicion.nombre}") @Size(max = 100) String nombre,
        /** Nulo = activo. */
        Boolean activo) {
}
