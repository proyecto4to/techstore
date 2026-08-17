# Fase 9 — Dispositivos, push notifications y deep links

**Estado:** completada y validada  
**Fecha de cierre:** 2026-08-12  
**Alcance:** registro seguro de instalaciones móviles, proveedor push intercambiable, consentimiento contextual y rutas profundas.

## Resultado

El usuario autenticado puede activar notificaciones desde Mi cuenta en un dispositivo físico. La app crea primero el canal Android requerido, solicita permiso solo después de explicar el beneficio, obtiene el Expo push token usando el `projectId` real de EAS y registra/rota la instalación en Spring Boot. El logout intenta desactivar esa instalación antes de revocar la sesión.

El backend conserva el dueño, tenant, plataforma, token actual, identificador lógico, estado y último uso. Nunca confía en tenant/usuario enviados por el móvil y nunca devuelve el push token en una respuesta.

## Backend

- Migración `V40__dispositivos_moviles_push.sql` con tabla `dispositivos_moviles`, índices parciales y RLS.
- `PUT /api/v1/dispositivos/actual`: alta o rotación idempotente por instalación.
- `GET /api/v1/dispositivos`: instalaciones activas propias, sin tokens.
- `DELETE /api/v1/dispositivos/actual/{deviceIdentifier}`: desactivación propia en logout.
- `PushProvider` desacopla la lógica comercial de Expo/FCM.
- `NoOpPushProvider` es el default local seguro; no realiza tráfico externo.
- `ExpoPushProvider` usa timeout, payload tipado, deep link en `data.url` y token de acceso opcional solo desde entorno backend.
- `PushNotificationService` deduplica tokens y desactiva `DeviceNotRegistered`; una caída del proveedor no se propaga a la operación comercial.

La entrega durable de eventos, tickets/receipts y retries pertenece a Fase 10 y se conectará mediante outbox. No se agregó una llamada push síncrona dentro de checkout, pedidos ni chat.

## Móvil

- Canal Android `techstore-default` creado antes de pedir/obtener token.
- `projectId` real `fd7d5b88-62cc-453e-834d-1940a56aaebc` leído desde Expo Constants.
- Identificador lógico de instalación persistido en SecureStore; no es una credencial de autenticación.
- Activación contextual desde Mi cuenta; no se solicita permiso al abrir la app.
- Sincronización silenciosa solo cuando el permiso ya fue concedido.
- Configuración nativa del plugin con icono TechStore, color `#0F66E6` y canal por defecto.
- Handler foreground para banner/lista/sonido/badge y listener de respuesta para navegación.
- Parser allowlist que acepta únicamente IDs numéricos en:
  - `techstore://producto/{id}`
  - `techstore://pedido/{id}`
  - `techstore://chat/{id}`
  - `techstore://notificacion/{id}`
- URLs web, query injection, rutas administrativas e IDs no numéricos se rechazan.
- El deep link de notificación queda preparado y apunta temporalmente a Home; Fase 11 lo conectará al centro persistente.

## Configuración

```properties
PUSH_PROVIDER=none
EXPO_PUSH_URL=https://exp.host/--/api/v2/push/send
EXPO_ACCESS_TOKEN=
```

Para entrega Expo real se cambia `PUSH_PROVIDER=expo`. `EXPO_ACCESS_TOKEN` solo se configura si el proyecto Expo usa seguridad reforzada; nunca se coloca en `EXPO_PUBLIC_*`, el APK ni Git. Android requiere credenciales FCM configuradas en EAS/Expo para la entrega real; no se inventaron ni incorporaron al repositorio.

## Evidencia de validación

| Control | Resultado |
| --- | --- |
| Backend compile | 298 fuentes Java compiladas correctamente. |
| Backend unitario | 306/306 pruebas aprobadas; 6 pertenecen a dispositivos/push. |
| Migraciones PostgreSQL 15 | 40 migraciones validadas; `MigracionesIT` 13/13. |
| TypeScript móvil | Sin errores. |
| ESLint móvil | Sin errores ni warnings. |
| Jest móvil | 18 suites/42 pruebas; 9 casos de deep links. |
| Expo Doctor | 20/20 comprobaciones. |
| Export móvil | 37 rutas estáticas. |
| Frontend web | 6 archivos/12 pruebas, lint y build aprobados. |
| Config Expo resuelta | SDK 57, package/scheme/projectId y plugin de notificaciones correctos. |

## Seguridad y límites

- Los push tokens se almacenan solo en backend y no aparecen en DTO de salida.
- El endpoint exige JWT; usuario y tenant salen de la sesión autenticada.
- Un dispositivo ajeno no puede desactivarse con solo conocer su identificador.
- No se solicitan cámara, micrófono, contactos, ubicación ni almacenamiento completo.
- Las notificaciones no sustituyen REST: tocar un aviso abre una ruta autorizada y la pantalla vuelve a consultar el backend.
- La entrega real a Android debe validarse en un build que incluya Fase 9 y con FCM configurado; el APK post-Fase 8 generado en paralelo no contiene esta fase.

## Archivos principales

- `techstore-backend/src/main/resources/db/migration/V40__dispositivos_moviles_push.sql`
- `techstore-backend/src/main/java/com/techstore/notificaciones/DispositivoMovil*.java`
- `techstore-backend/src/main/java/com/techstore/notificaciones/Push*.java`
- `techstore-mobile/src/features/notifications/`
- `techstore-mobile/src/app/{producto,pedido,chat,notificacion}/[id].tsx`
- `techstore-mobile/tests/deepLinks.test.ts`

## Criterios de aceptación

- [x] Registro, rotación, listado seguro y logout de instalaciones.
- [x] Aislamiento tenant mediante Hibernate y PostgreSQL RLS.
- [x] Proveedor Expo sustituible por otro `PushProvider`.
- [x] Consentimiento contextual y sincronización sin prompts inesperados.
- [x] Deep links limitados, probados y compatibles con Expo Router.
- [x] Backend, web, móvil, Doctor, export y migraciones en verde.

La Fase 10 añadirá outbox transaccional, eventos de dominio, email/push asíncronos, tickets/receipts y retries.
