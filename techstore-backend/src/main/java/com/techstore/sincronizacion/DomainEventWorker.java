package com.techstore.sincronizacion;

import com.techstore.comun.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DomainEventWorker {
    private final DomainEventStore store;
    private final SimpMessagingTemplate messaging;
    @Value("${sync.outbox.enabled:true}") private boolean enabled;
    @Value("${sync.outbox.batch-size:100}") private int batchSize;
    @Value("${sync.outbox.stale-after-seconds:60}") private long staleSeconds;

    @Scheduled(fixedDelayString = "${sync.outbox.poll-interval-ms:750}",
            initialDelayString = "${sync.outbox.initial-delay-ms:3000}")
    public void deliver() {
        if (!enabled) return;
        try {
            for (Long tenantId : store.tenantsActivos()) {
                TenantContext.comoTenant(tenantId, () -> {
                    for (DomainEventStore.Claimed claimed : store.claim(batchSize, staleSeconds)) deliver(claimed);
                    return null;
                });
            }
        } catch (RuntimeException exception) {
            log.warn("No se pudo procesar el outbox de sincronización: {}", safe(exception));
        }
    }

    private void deliver(DomainEventStore.Claimed claimed) {
        TenantContext.comoTenant(claimed.tenantId(), () -> {
            try {
                DomainEventResponse event = store.find(claimed.id());
                messaging.convertAndSend("/topic/tenant/" + claimed.tenantId() + "/events", event);
                store.processed(claimed.id());
            } catch (RuntimeException exception) {
                store.retry(claimed.id(), safe(exception));
            }
            return null;
        });
    }

    private String safe(RuntimeException exception) {
        String value = exception.getClass().getSimpleName();
        return value.length() > 300 ? value.substring(0, 300) : value;
    }
}
