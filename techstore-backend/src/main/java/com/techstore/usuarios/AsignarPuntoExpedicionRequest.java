package com.techstore.usuarios;

/**
 * Desde qué punto de expedición emite este usuario.
 *
 * @param puntoExpedicionId nulo a propósito: significa "el por defecto de la
 *                          empresa", que es como venían funcionando todos los
 *                          usuarios antes de que hubiera más de un punto. Por eso
 *                          no lleva {@code @NotNull}.
 */
public record AsignarPuntoExpedicionRequest(Long puntoExpedicionId) {
}
