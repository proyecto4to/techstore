package com.techstore.notificaciones.outbox;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationOutboxWorkerTest {

    @Test
    void successfulDeliveryMarksTheClaimAsProcessed() {
        OutboxStore store = mock(OutboxStore.class);
        NotificationOutboxRepository repository = mock(NotificationOutboxRepository.class);
        NotificationOutboxDispatcher dispatcher = mock(NotificationOutboxDispatcher.class);
        NotificationOutbox outbox = event(31L);
        when(store.claim(20, 900)).thenReturn(List.of(new OutboxStore.ClaimedJob(31L, 4L, 1)));
        when(repository.findById(31L)).thenReturn(Optional.of(outbox));
        NotificationOutboxWorker worker = worker(store, repository, dispatcher);

        worker.processDue();

        verify(dispatcher).dispatch(outbox);
        verify(store).markProcessed(31L, 4L);
    }

    @Test
    void failedDeliveryUsesExponentialRetryAndSanitizesSecrets() {
        OutboxStore store = mock(OutboxStore.class);
        NotificationOutboxRepository repository = mock(NotificationOutboxRepository.class);
        NotificationOutboxDispatcher dispatcher = mock(NotificationOutboxDispatcher.class);
        NotificationOutbox outbox = event(32L);
        when(store.claim(20, 900)).thenReturn(List.of(new OutboxStore.ClaimedJob(32L, 4L, 3)));
        when(repository.findById(32L)).thenReturn(Optional.of(outbox));
        doThrow(new RuntimeException("provider failed token=super-secret\nretry"))
                .when(dispatcher).dispatch(outbox);
        NotificationOutboxWorker worker = worker(store, repository, dispatcher);

        worker.processDue();

        var error = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(store).markFailed(eq(32L), eq(4L), eq(false), eq(120L), error.capture());
        assertFalse(error.getValue().contains("super-secret"));
        assertFalse(error.getValue().contains("\n"));
    }

    @Test
    void backoffIsBounded() {
        assertEquals(30, NotificationOutboxWorker.backoffSeconds(1, 30, 21_600));
        assertEquals(120, NotificationOutboxWorker.backoffSeconds(3, 30, 21_600));
        assertEquals(21_600, NotificationOutboxWorker.backoffSeconds(30, 30, 21_600));
    }

    private NotificationOutboxWorker worker(OutboxStore store,
                                             NotificationOutboxRepository repository,
                                             NotificationOutboxDispatcher dispatcher) {
        return new NotificationOutboxWorker(
                store, repository, dispatcher, true, 20, 5, 30, 21_600, 900);
    }

    private NotificationOutbox event(Long id) {
        NotificationOutbox outbox = new NotificationOutbox();
        outbox.setId(id);
        outbox.setEventType(NotificationEventType.PEDIDO_CREADO);
        outbox.setConsumerChannel(OutboxChannel.EMAIL);
        return outbox;
    }
}
