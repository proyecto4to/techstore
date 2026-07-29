package com.techstore.dto.response;

import com.techstore.entity.AperturaCaja;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Turno de caja abierto. {@code abierta} en false significa que no hay ninguno. */
public record AperturaCajaResponse(
        boolean abierta,
        Long id,
        Integer numero,
        LocalDateTime fechaApertura,
        BigDecimal saldoInicial,
        String usuario,
        String notas) {

    public static AperturaCajaResponse from(AperturaCaja a) {
        return new AperturaCajaResponse(
                true,
                a.getId(),
                a.getNumero(),
                a.getFechaApertura(),
                a.getSaldoInicial(),
                a.getUsuario().getNombre() + " " + a.getUsuario().getApellido(),
                a.getNotas());
    }

    /** No hay turno abierto. */
    public static AperturaCajaResponse cerrada() {
        return new AperturaCajaResponse(false, null, null, null, null, null, null);
    }
}
