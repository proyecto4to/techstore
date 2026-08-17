package com.techstore.notificaciones.outbox;

import com.techstore.comun.tenant.TenantContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class NotificationOutboxWorker {
    private final OutboxStore store;
    private final NotificationOutboxRepository repository;
    private final NotificationOutboxDispatcher dispatcher;
    private final boolean enabled;
    private final int batchSize;
    private final int maxAttempts;
    private final long baseRetrySeconds;
    private final long maxRetrySeconds;
    private final long staleAfterSeconds;

    public NotificationOutboxWorker(
            OutboxStore store,
            NotificationOutboxRepository repository,
            NotificationOutboxDispatcher dispatcher,
            @Value("${notifications.outbox.enabled:true}") boolean enabled,
            @Value("${notifications.outbox.batch-size:20}") int batchSize,
            @Value("${notifications.outbox.max-attempts:5}") int maxAttempts,
            @Value("${notifications.outbox.base-retry-seconds:30}") long baseRetrySeconds,
            @Value("${notifications.outbox.max-retry-seconds:21600}") long maxRetrySeconds,
            @Value("${notifications.outbox.stale-after-seconds:900}") long staleAfterSeconds) {
        this.store = store;
        this.repository = repository;
        this.dispatcher = dispatcher;
        this.enabled = enabled;
        this.batchSize = batchSize;
        this.maxAttempts = maxAttempts;
        this.baseRetrySeconds = baseRetrySeconds;
        this.maxRetrySeconds = maxRetrySeconds;
        this.staleAfterSeconds = staleAfterSeconds;
    }

    /**
     * Busca trabajo empresa por empresa.
     *
     * <p>Reclamar de una sola pasada, sin empresa fijada, no devuelve nada: las
     * políticas de Row Level Security del outbox exigen que la conexión declare a
     * qué empresa pertenece, y una tarea programada no hereda ese dato de ninguna
     * petición. El síntoma no es un error sino silencio —el reclamo trae cero
     * candidatos cada vez— así que los eventos quedan pendientes para siempre y
     * ningún correo ni push sale del sistema.
     */
    @Scheduled(fixedDelayString = "${notifications.outbox.poll-interval-ms:5000}",
            initialDelayString = "${notifications.outbox.initial-delay-ms:10000}")
    public void processDue() {
        if (!enabled) return;
        try {
            for (Long tenantId : store.tenantsActivos()) {
                TenantContext.comoTenant(tenantId, () -> {
                    for (OutboxStore.ClaimedJob job : store.claim(batchSize, staleAfterSeconds)) {
                        process(job);
                    }
                    return null;
                });
            }
        } catch (RuntimeException exception) {
            log.warn("No se pudo consultar el outbox de notificaciones: {}", exception.getMessage());
        }
    }

    private void process(OutboxStore.ClaimedJob job) {
        TenantContext.comoTenant(job.tenantId(), () -> {
            try {
                NotificationOutbox outbox = repository.findById(job.id())
                        .orElseThrow(() -> new IllegalStateException("Evento outbox inexistente"));
                dispatcher.dispatch(outbox);
                store.markProcessed(job.id(), job.tenantId());
                log.info("Outbox {} {} entregado por {} en intento {}", job.id(),
                        outbox.getEventType(), outbox.getConsumerChannel(), job.attempts());
            } catch (RuntimeException exception) {
                boolean exhausted = job.attempts() >= maxAttempts;
                long delay = backoffSeconds(job.attempts(), baseRetrySeconds, maxRetrySeconds);
                String error = OutboxErrorSanitizer.sanitize(exception);
                store.markFailed(job.id(), job.tenantId(), exhausted, delay, error);
                log.warn("Outbox {} falló en intento {} ({}): {}", job.id(), job.attempts(),
                        exhausted ? "agotado" : "reintento en " + delay + "s", error);
            }
            return null;
        });
    }

    static long backoffSeconds(int attempt, long base, long maximum) {
        int exponent = Math.max(0, Math.min(attempt - 1, 30));
        long multiplier = 1L << exponent;
        if (base > maximum / multiplier) return maximum;
        return Math.min(maximum, base * multiplier);
    }
}
