package com.techstore.notificaciones;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Notification;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@ConditionalOnProperty(prefix = "push", name = "provider", havingValue = "fcm")
public class FcmPushProvider implements PushProvider {

    private final FirebaseMessaging messaging;

    @Autowired
    public FcmPushProvider() {
        if (FirebaseApp.getApps().isEmpty()) {
            FirebaseApp.initializeApp();
        }
        this.messaging = FirebaseMessaging.getInstance();
    }

    FcmPushProvider(FirebaseMessaging messaging) {
        this.messaging = messaging;
    }

    @Override
    public PushResult enviar(PushMessage mensaje) {
        try {
            Map<String, String> data = new LinkedHashMap<>();
            mensaje.datos().forEach((key, value) -> {
                if (value != null) data.put(key, String.valueOf(value));
            });
            if (mensaje.deepLink() != null && !mensaje.deepLink().isBlank()) {
                data.put("url", mensaje.deepLink());
            }
            Message request = Message.builder()
                    .setToken(mensaje.token())
                    .setNotification(Notification.builder()
                            .setTitle(mensaje.titulo())
                            .setBody(mensaje.cuerpo())
                            .build())
                    .putAllData(data)
                    .build();
            return PushResult.aceptado(messaging.send(request));
        } catch (FirebaseMessagingException exception) {
            MessagingErrorCode code = exception.getMessagingErrorCode();
            boolean permanente = code == MessagingErrorCode.UNREGISTERED
                    || code == MessagingErrorCode.INVALID_ARGUMENT;
            return PushResult.error(code == null ? "FCM_ERROR" : "FCM_" + code.name(), permanente);
        } catch (RuntimeException exception) {
            return PushResult.error("FCM_UNAVAILABLE", false);
        }
    }
}
