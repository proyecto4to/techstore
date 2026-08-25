package com.techstore.sincronizacion;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DomainEventWorkerTest {
    @Test
    void entregaEventoAlTopicDelTenantYMarcaProcesado() {
        DomainEventStore store = mock(DomainEventStore.class);
        SimpMessagingTemplate messaging = mock(SimpMessagingTemplate.class);
        DomainEventWorker worker = new DomainEventWorker(store, messaging);
        ReflectionTestUtils.setField(worker, "enabled", true);
        ReflectionTestUtils.setField(worker, "batchSize", 100);
        ReflectionTestUtils.setField(worker, "staleSeconds", 60L);
        var event = new DomainEventResponse(9L, "PRODUCT_UPDATED", "PRODUCT", 3L,
                Map.of("operation", "UPDATE"), LocalDateTime.now());
        when(store.tenantsActivos()).thenReturn(List.of(7L));
        when(store.claim(100, 60L)).thenReturn(List.of(new DomainEventStore.Claimed(9L, 7L, 1)));
        when(store.find(9L)).thenReturn(event);

        worker.deliver();

        verify(messaging).convertAndSend("/topic/tenant/7/events", event);
        verify(store).processed(9L);
    }
}
