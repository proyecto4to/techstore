package com.techstore.empresa;

import java.math.BigDecimal;

/**
 * La ubicación guardada. Viene con {@code definida} en falso mientras nadie la
 * haya marcado, para que la pantalla muestre «Sin ubicación» en lugar de un
 * mapa centrado en el punto cero del Atlántico.
 */
public record UbicacionEmpresaResponse(
        boolean definida,
        BigDecimal latitud,
        BigDecimal longitud,
        String referencia) {

    public static UbicacionEmpresaResponse sinDefinir() {
        return new UbicacionEmpresaResponse(false, null, null, null);
    }
}
