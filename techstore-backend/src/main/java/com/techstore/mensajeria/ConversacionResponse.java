package com.techstore.mensajeria;

import java.time.LocalDateTime;

public record ConversacionResponse(
        Long id,
        String estado,
        Long clienteId,
        String clienteNombre,
        String clienteEmail,
        Long pedidoId,
        Integer pedidoNumero,
        MensajeResponse ultimoMensaje,
        long noLeidos,
        LocalDateTime ultimoMensajeEn,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) { }
