package com.techstore.sincronizacion;

import java.time.LocalDateTime;
import java.util.Map;

public record DomainEventResponse(
        Long id,
        String eventType,
        String aggregateType,
        Long aggregateId,
        Map<String, Object> payload,
        LocalDateTime occurredAt) {
}
