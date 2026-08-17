package com.techstore.notificaciones.outbox;

public enum OutboxStatus {
    PENDING,
    PROCESSING,
    PROCESSED,
    EXHAUSTED
}
