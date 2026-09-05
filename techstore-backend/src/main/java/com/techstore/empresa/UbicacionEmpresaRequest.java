package com.techstore.empresa;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Dónde queda la empresa, tal como la deja marcada el administrador en el mapa.
 *
 * <p>Los rangos no son decorativos: sin ellos, una coma mal puesta guarda una
 * ubicación en medio del océano y el mapa aparece vacío sin decir por qué.
 */
public record UbicacionEmpresaRequest(
        @NotNull(message = "{validacion.ubicacion.obligatoria}")
        @DecimalMin(value = "-90.0", message = "{validacion.latitud.rango}")
        @DecimalMax(value = "90.0", message = "{validacion.latitud.rango}")
        BigDecimal latitud,

        @NotNull(message = "{validacion.ubicacion.obligatoria}")
        @DecimalMin(value = "-180.0", message = "{validacion.longitud.rango}")
        @DecimalMax(value = "180.0", message = "{validacion.longitud.rango}")
        BigDecimal longitud,

        /** Cómo describirla en palabras, para quien no quiere abrir el mapa. */
        @Size(max = 255) String referencia) {
}
