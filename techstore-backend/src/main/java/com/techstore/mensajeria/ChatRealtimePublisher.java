package com.techstore.mensajeria;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Collection;

@Component
@RequiredArgsConstructor
public class ChatRealtimePublisher {
    private final SimpMessagingTemplate template;

    public void mensaje(Collection<String> destinatarios, MensajeResponse response) {
        despuesDelCommit(() -> destinatarios.stream().distinct().forEach(email ->
                template.convertAndSendToUser(email, "/queue/chat", response)));
    }

    public void lectura(Collection<String> destinatarios, LecturaResponse response) {
        despuesDelCommit(() -> destinatarios.stream().distinct().forEach(email ->
                template.convertAndSendToUser(email, "/queue/chat-read", response)));
    }

    private void despuesDelCommit(Runnable accion) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            accion.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { accion.run(); }
        });
    }
}
