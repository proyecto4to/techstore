package com.techstore.comun;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Collection;

/**
 * Deja algo en la cola personal de cada destinatario, recién cuando la
 * transacción confirmó.
 *
 * <p>El orden importa: si se manda antes del commit, el cliente recibe el
 * empujón, pide el dato por HTTP y todavía no está —o peor, la transacción
 * termina revirtiendo y el aviso queda hablando de algo que nunca pasó—.
 *
 * <p>La cola personal es el canal preciso: llega solo a quien tiene que
 * enterarse, sin pasar por el flujo de eventos de toda la empresa. Por eso el
 * chat y las notificaciones no necesitan disparador en la base: ya saben a
 * quién le toca cada cosa.
 */
@Component
@RequiredArgsConstructor
public class EnvioTrasCommit {
    private final SimpMessagingTemplate template;

    public void aCadaUno(Collection<String> emails, String cola, Object cuerpo) {
        despuesDelCommit(() -> emails.stream().distinct()
                .forEach(email -> template.convertAndSendToUser(email, cola, cuerpo)));
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
