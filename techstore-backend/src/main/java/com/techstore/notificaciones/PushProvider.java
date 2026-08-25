package com.techstore.notificaciones;

/** Punto de sustitución entre FCM para Flutter y Expo para el cliente legado. */
public interface PushProvider {
    PushResult enviar(PushMessage mensaje);
}
