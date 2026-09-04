package com.techstore.catalogo;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ProductoRequest(
        @NotBlank @Size(max = 255) String nombre,
        String descripcion,
        @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal precio,
        @NotNull @Min(0) @Max(100) Integer iva,
        @NotNull @Min(0) Integer stockReal,
        /** Punto de reposición. Opcional: si no viene, queda en 0 y el producto no avisa. */
        @Min(0) Integer stockMinimo,
        @DecimalMin("0.0") BigDecimal pesoKg,
        Long marcaId,
        Long modeloId,
        @Size(max = 500) String imagenUrl,
        Boolean activo,
        /** Si desaparece de la tienda al quedarse sin stock. Nulo deja el valor actual. */
        Boolean ocultarSinStock,
        /**
         * Si se ofrece en la app de clientes. Nulo deja el valor actual.
         *
         * <p>Distinto de {@code activo}: en falso el producto sigue operando
         * en el mostrador y en el inventario, pero sale de la vidriera.
         */
        Boolean visibleEnApp) {

    /** Compatibilidad con clientes anteriores a la visibilidad en la app. */
    public ProductoRequest(String nombre, String descripcion, BigDecimal precio, Integer iva,
                           Integer stockReal, Integer stockMinimo, BigDecimal pesoKg, Long marcaId,
                           Long modeloId, String imagenUrl, Boolean activo, Boolean ocultarSinStock) {
        this(nombre, descripcion, precio, iva, stockReal, stockMinimo, pesoKg, marcaId, modeloId,
                imagenUrl, activo, ocultarSinStock, null);
    }

    /** Compatibilidad binaria de tests y clientes internos anteriores al peso logístico. */
    public ProductoRequest(String nombre, String descripcion, BigDecimal precio, Integer iva,
                           Integer stockReal, Integer stockMinimo, Long marcaId, Long modeloId,
                           String imagenUrl, Boolean activo, Boolean ocultarSinStock) {
        this(nombre, descripcion, precio, iva, stockReal, stockMinimo, BigDecimal.ZERO,
                marcaId, modeloId, imagenUrl, activo, ocultarSinStock, null);
    }
}
