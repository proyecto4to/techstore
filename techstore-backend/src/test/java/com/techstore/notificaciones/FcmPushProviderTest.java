package com.techstore.notificaciones;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FcmPushProviderTest {

    @Test
    void entregaNotificacionYDatosPorFirebase() throws Exception {
        FirebaseMessaging messaging = mock(FirebaseMessaging.class);
        when(messaging.send(any(Message.class))).thenReturn("projects/techstore/messages/1");
        FcmPushProvider provider = new FcmPushProvider(messaging);

        PushResult result = provider.enviar(new PushMessage(
                "fcm-token-valido-con-mas-de-veinte-caracteres",
                "Pedido listo",
                "Tu compra ya puede retirarse",
                "techstore://pedido/12",
                Map.of("pedidoId", 12)));

        assertTrue(result.aceptado());
        assertEquals("projects/techstore/messages/1", result.ticketId());
        verify(messaging).send(any(Message.class));
    }
}
