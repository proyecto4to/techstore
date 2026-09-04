package com.techstore.catalogo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ProductoResponse(
        Long id,
        String nombre,
        String descripcion,
        String moneda,
        BigDecimal precio,
        /** Costo de la última compra recibida; nulo mientras no se haya comprado. */
        BigDecimal costo,
        LocalDateTime costoActualizadoEn,
        /** Precio sin IVA menos costo: lo que deja la venta. Nulo si no hay costo. */
        BigDecimal margen,
        /** Si desaparece de la tienda al quedarse sin stock. */
        Boolean ocultarSinStock,
        /** Si se ofrece en la app de clientes. */
        Boolean visibleEnApp,
        Integer iva,
        Integer stockReal,
        Integer stockComprometido,
        Integer stockDisponible,
        Integer stockMinimo,
        BigDecimal pesoKg,
        boolean necesitaReposicion,
        MarcaRef marca,
        ModeloRef modelo,
        String imagenUrl,
        Boolean activo,
        LocalDateTime createdAt) {

    public record MarcaRef(Long id, String nombre) {
    }

    public record ModeloRef(Long id, String nombre) {
    }
}
