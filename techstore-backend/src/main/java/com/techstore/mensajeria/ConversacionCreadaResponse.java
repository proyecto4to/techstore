package com.techstore.mensajeria;

public record ConversacionCreadaResponse(
        ConversacionResponse conversacion,
        MensajeResponse mensaje) { }
