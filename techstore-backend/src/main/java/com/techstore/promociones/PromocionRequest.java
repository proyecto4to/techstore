package com.techstore.promociones;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Lo que administración carga para crear o editar una promoción.
 *
 * <p>{@code productoId} y {@code marcaId} se completan según el alcance; el
 * servicio rechaza las combinaciones que no corresponden en vez de ignorarlas
 * en silencio, para que nadie crea que dejó una promoción acotada a una marca
 * cuando en realidad la dejó suelta sobre todo el catálogo.
 */
public record PromocionRequest(
        @NotBlank @Size(max = 120) String nombre,
        @NotNull @DecimalMin(value = "0.01") @DecimalMax(value = "90.00") BigDecimal porcentaje,
        @NotNull AlcancePromocion alcance,
        Long productoId,
        Long marcaId,
        @NotNull LocalDateTime desde,
        @NotNull LocalDateTime hasta,
        Boolean activa) {
}
