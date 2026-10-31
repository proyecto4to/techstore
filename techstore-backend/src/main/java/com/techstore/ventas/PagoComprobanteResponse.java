package com.techstore.ventas;

import java.time.LocalDateTime;

/**
 * Un comprobante adjunto, como lo ve la aplicación.
 *
 * <p>No lleva el nombre del archivo en disco: al cliente le sirve el id para
 * pedirlo o borrarlo, y exponer la ruta interna no le aporta nada.
 */
public record PagoComprobanteResponse(
        Long id,
        String tipo,
        long tamanoBytes,
        LocalDateTime subidoEn,
        /** EN_REVISION, APROBADO o RECHAZADO. */
        String estado,
        /** Por qué no se validó; nulo si no fue rechazado. */
        String motivoRechazo) {

    public static PagoComprobanteResponse from(PagoComprobante comprobante) {
        return new PagoComprobanteResponse(
                comprobante.getId(),
                comprobante.getArchivoContentType(),
                comprobante.getTamanoBytes(),
                comprobante.getCreatedAt(),
                comprobante.getEstado().name(),
                comprobante.getMotivoRechazo());
    }
}
