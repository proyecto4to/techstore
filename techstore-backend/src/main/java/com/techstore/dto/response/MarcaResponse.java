package com.techstore.dto.response;

import com.techstore.entity.Marca;

public record MarcaResponse(
        Long id,
        String nombre,
        String descripcion) {

    public static MarcaResponse from(Marca marca) {
        return new MarcaResponse(marca.getId(), marca.getNombre(), marca.getDescripcion());
    }
}
