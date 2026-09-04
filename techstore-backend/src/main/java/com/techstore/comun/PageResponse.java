package com.techstore.comun;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }

    /**
     * La misma pagina con cada elemento transformado, conservando el conteo.
     *
     * <p>Sirve para recortar por permisos lo que se devuelve sin tocar la
     * consulta: la cantidad de resultados y el paginado siguen siendo los
     * reales, cambia solo el contenido de cada fila.
     */
    public <R> PageResponse<R> map(Function<? super T, ? extends R> conversion) {
        return new PageResponse<>(
                content.stream().<R>map(conversion).toList(),
                page,
                size,
                totalElements,
                totalPages);
    }
}
