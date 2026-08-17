package com.techstore.envios;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record EnvioResponse(
        Long id,
        Long pedidoId,
        Integer pedidoNumero,
        String tipo,
        String providerCode,
        String transportista,
        String codigoSeguimiento,
        BigDecimal costo,
        String moneda,
        BigDecimal iva,
        LocalDate fechaEstimadaDesde,
        LocalDate fechaEstimadaHasta,
        LocalDateTime fechaDespacho,
        LocalDateTime fechaEntrega,
        String estado,
        String observaciones,
        List<EventoResponse> eventos) {
    public record EventoResponse(Long id, String estado, String descripcion,
                                 LocalDateTime fechaHora, String ubicacion) { }
}
