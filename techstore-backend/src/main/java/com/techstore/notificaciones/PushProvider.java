package com.techstore.notificaciones;

/** Punto de sustitución entre Expo Push Service y un futuro FCM directo. */
public interface PushProvider {
    PushResult enviar(PushMessage mensaje);
}
