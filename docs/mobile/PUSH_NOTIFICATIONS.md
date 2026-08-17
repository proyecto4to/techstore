# Push notifications

## Flujo

```text
TechStore Mobile
  -> permiso contextual del sistema
  -> Expo push token + installation identifier
  -> PUT /api/v1/dispositivos/actual (JWT)
  -> dispositivos_moviles (tenant + usuario + RLS)

Evento de dominio / Outbox (Fase 10)
  -> PushNotificationService
  -> PushProvider
  -> Expo Push Service
  -> FCM/APNs
  -> data.url = techstore://...
```

## Principios

- El backend es el único emisor de push y dueño de reglas/destinatarios.
- El móvil nunca contiene secretos de Expo, Firebase ni credenciales de servidor.
- Un push es aviso, no fuente de verdad: la pantalla siempre recupera datos autorizados por API.
- Solicitar permiso requiere una acción y explicación previa del usuario.
- Tokens rotados o `DeviceNotRegistered` se desactivan.
- Checkout, chat y cambios de pedido no esperan a un servicio externo.

## Entornos

`PUSH_PROVIDER=none` mantiene desarrollo local sin llamadas externas. Para Expo:

```properties
PUSH_PROVIDER=expo
EXPO_PUSH_URL=https://exp.host/--/api/v2/push/send
EXPO_ACCESS_TOKEN=
```

`EXPO_ACCESS_TOKEN` es secreto backend. No usar `EXPO_PUBLIC_`.

Además de un build nativo, Android necesita credenciales FCM V1 configuradas en EAS/Expo. iOS requerirá credenciales APNs de Apple. Ninguna se guarda en el repositorio.

## Deep links permitidos

| Tipo | URL pública | Pantalla interna |
| --- | --- | --- |
| Producto | `techstore://producto/{id}` | detalle de producto |
| Pedido | `techstore://pedido/{id}` | detalle de pedido |
| Chat | `techstore://chat/{id}` | conversación |
| Notificación | `techstore://notificacion/{id}` | centro de Fase 11 |

Solo se aceptan IDs positivos expresados con dígitos y el scheme exacto `techstore://`.

## Operación

- Crear el canal Android antes de solicitar token, especialmente en Android 13+.
- Usar el `extra.eas.projectId` real al llamar `getExpoPushTokenAsync`.
- Consultar tickets y receipts; un ticket aceptado no garantiza entrega al teléfono.
- Procesar `DeviceNotRegistered` como error permanente.
- Mantener timeouts, lotes, backoff y límite de reintentos en el worker de outbox.

Referencias oficiales verificadas para Expo SDK 57:

- <https://docs.expo.dev/versions/v57.0.0/sdk/notifications/>
- <https://docs.expo.dev/versions/v57.0.0/sdk/linking/>
- <https://docs.expo.dev/push-notifications/push-notifications-setup/>
- <https://docs.expo.dev/push-notifications/sending-notifications/>
