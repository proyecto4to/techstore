package com.techstore.empresa;

/**
 * Un punto de expedición y con qué prefijo salen sus documentos.
 *
 * @param porDefecto el que se usa cuando quien emite no tiene uno asignado
 */
public record PuntoExpedicionResponse(
        Long id,
        String establecimiento,
        String punto,
        String prefijo,
        String nombre,
        boolean activo,
        boolean porDefecto) {

    public static PuntoExpedicionResponse from(PuntoExpedicion p) {
        return new PuntoExpedicionResponse(
                p.getId(), p.getEstablecimiento(), p.getPunto(), p.prefijo(),
                p.getNombre(), Boolean.TRUE.equals(p.getActivo()), Boolean.TRUE.equals(p.getPorDefecto()));
    }
}
