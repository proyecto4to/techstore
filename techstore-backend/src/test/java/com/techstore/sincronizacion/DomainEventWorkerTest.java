package com.techstore.sincronizacion;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DomainEventWorkerTest {
    private final DomainEventStore store = mock(DomainEventStore.class);
    private final SimpMessagingTemplate messaging = mock(SimpMessagingTemplate.class);

    @Test
    void entregaEventoAlTopicDelTenantYMarcaProcesado() {
        var event = new DomainEventResponse(9L, "PRODUCT_UPDATED", "PRODUCT", 3L,
                Map.of("operation", "UPDATE"), LocalDateTime.now());

        entregar(event);

        verify(messaging).convertAndSend("/topic/tenant/7/events", event);
        verify(store).processed(9L);
    }

    /**
     * El catalogo tambien viaja por el canal acotado, que es al que se suscribe
     * quien compra: es lo que hace que el stock baje solo en la pantalla del
     * resto sin recargar nada.
     */
    @Test
    void loQuePuedeVerQuienCompraViajaPorLosDosCanales() {
        var event = new DomainEventResponse(9L, "STOCK_UPDATED", "PRODUCT", 3L,
                Map.of("stockReal", 7), LocalDateTime.now());

        entregar(event);

        verify(messaging).convertAndSend("/topic/tenant/7/events", event);
        verify(messaging).convertAndSend("/topic/tenant/7/events/compra", event);
    }

    /**
     * Un movimiento de caja no sale del canal completo. El cuerpo del evento es
     * flaco, pero que llegue ya cuenta que el negocio cobro y a que hora.
     */
    @Test
    void loDelMostradorNoSaleDelCanalCompleto() {
        var event = new DomainEventResponse(9L, "CASH_MOVEMENT_CREATED", "CASH_MOVEMENT", 3L,
                Map.of("operation", "INSERT"), LocalDateTime.now());

        entregar(event);

        verify(messaging).convertAndSend("/topic/tenant/7/events", event);
        verify(messaging, never()).convertAndSend(eq("/topic/tenant/7/events/compra"), any(Object.class));
    }

    private void entregar(DomainEventResponse event) {
        DomainEventWorker worker = new DomainEventWorker(store, messaging);
        ReflectionTestUtils.setField(worker, "enabled", true);
        ReflectionTestUtils.setField(worker, "batchSize", 100);
        ReflectionTestUtils.setField(worker, "staleSeconds", 60L);
        when(store.tenantsActivos()).thenReturn(List.of(7L));
        when(store.claim(100, 60L)).thenReturn(List.of(new DomainEventStore.Claimed(event.id(), 7L, 1)));
        when(store.find(event.id())).thenReturn(event);
        worker.deliver();
    }
}
