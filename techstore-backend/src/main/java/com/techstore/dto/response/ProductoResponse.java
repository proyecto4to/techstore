package com.techstore.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ProductoResponse(
        Long id,
        String nombre,
        String descripcion,
        BigDecimal precio,
        Integer iva,
        Integer stockReal,
        Integer stockComprometido,
        Integer stockDisponible,
        MarcaRef marca,
        ModeloRef modelo,
        Boolean activo,
        LocalDateTime createdAt) {

    public record MarcaRef(Long id, String nombre) {
    }

    public record ModeloRef(Long id, String nombre) {
    }
}
