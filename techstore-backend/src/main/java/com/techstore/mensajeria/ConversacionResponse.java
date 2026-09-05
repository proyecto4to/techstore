package com.techstore.mensajeria;

import java.time.LocalDateTime;

/**
 * Una conversación ya resuelta para quien la mira.
 *
 * <p>El título y el rol vienen calculados del servidor y no en crudo, porque el
 * cliente no debe ver conceptos internos: su conversación de soporte se llama
 * «Atención al Cliente» y no trae ni el nombre ni el rol de quien le responde.
 * Si eso se resolviera en la pantalla, bastaría mirar la respuesta de la API
 * para saltearlo.
 */
public record ConversacionResponse(
        Long id,
        String tipo,
        String estado,
        /** Cómo se llama la conversación para quien pregunta. */
        String titulo,
        /** Rol de la otra parte, o nulo cuando no corresponde mostrarlo. */
        String rolContraparte,
        Long contraparteId,
        String contraparteEmail,
        Long pedidoId,
        Integer pedidoNumero,
        MensajeResponse ultimoMensaje,
        long noLeidos,
        LocalDateTime ultimoMensajeEn,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) { }
