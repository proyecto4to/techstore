package com.techstore.mensajeria;

import com.techstore.comun.EnvioTrasCommit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;

/** Los dos avisos del chat que van a la cola personal de cada participante. */
@Component
@RequiredArgsConstructor
public class ChatRealtimePublisher {
    private final EnvioTrasCommit envio;

    public void mensaje(Collection<String> destinatarios, MensajeResponse response) {
        envio.aCadaUno(destinatarios, "/queue/chat", response);
    }

    public void lectura(Collection<String> destinatarios, LecturaResponse response) {
        envio.aCadaUno(destinatarios, "/queue/chat-read", response);
    }
}
