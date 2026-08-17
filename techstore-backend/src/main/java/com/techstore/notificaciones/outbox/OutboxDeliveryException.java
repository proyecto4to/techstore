package com.techstore.notificaciones.outbox;

public class OutboxDeliveryException extends RuntimeException {
    public OutboxDeliveryException(String message) {
        super(message);
    }
}
