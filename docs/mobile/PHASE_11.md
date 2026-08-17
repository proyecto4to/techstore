# Fase 11 — Centro de notificaciones y badges

**Estado:** completada y validada  
**Fecha de cierre:** 2026-08-13  
**Alcance:** notificaciones internas durables, navegación segura y contadores reales de mensajes/notificaciones.

## Resultado

TechStore Mobile dispone de un centro autenticado que lista notificaciones paginadas, diferencia leídas de no leídas, permite marcar una o todas y navega al pedido, conversación o producto relacionado mediante deep links validados.

Los badges del header dejaron de ser valores de demostración. El backend calcula los mensajes y las notificaciones no leídas del usuario autenticado; el móvil los consulta con TanStack Query, los refresca periódicamente e intenta sincronizar el badge del sistema operativo sin convertir esa capacidad opcional en un error de la aplicación.

## Persistencia y outbox

La migración `V42__centro_notificaciones.sql`:

- crea `notificaciones_usuario` con referencia al usuario y al evento outbox de origen;
- agrega el canal `INTERNAL` al constraint de `notification_outbox`;
- aplica unicidad `(tenant_id, source_outbox_id)` para entrega idempotente;
- agrega índices por usuario/fecha y usuario/estado no leído;
- habilita y fuerza RLS, con políticas separadas para tenant y mantenimiento.

El mismo publicador de Fase 10 genera trabajos `INTERNAL`. El dispatcher transforma cada evento en título, mensaje, referencia y deep link y persiste la notificación. Correo, push e interno conservan reintentos y estados independientes.

## API autenticada

| Método | Ruta | Función |
| --- | --- | --- |
| `GET` | `/api/v1/notificaciones` | Listar las notificaciones propias, paginadas. |
| `GET` | `/api/v1/notificaciones/no-leidas` | Contar notificaciones propias no leídas. |
| `GET` | `/api/v1/notificaciones/{id}` | Obtener una notificación propia. |
| `PUT` | `/api/v1/notificaciones/{id}/leida` | Marcar una notificación propia como leída. |
| `PUT` | `/api/v1/notificaciones/leidas` | Marcar todas las propias como leídas. |
| `GET` | `/api/v1/conversaciones/no-leidos` | Contar mensajes no leídos según el rol autenticado. |

Ningún endpoint acepta `usuarioId` ni `tenantId` del cliente. El usuario se resuelve desde `Authentication`, las consultas se limitan a su propietario y PostgreSQL mantiene el aislamiento adicional mediante RLS.

## Aplicación móvil

- Pantalla `Notificaciones` con paginación incremental, pull-to-refresh, skeleton, estados vacío/error y acción “Marcar todas”.
- La apertura marca el elemento como leído e invalida el cache del centro y del contador.
- La ruta `techstore://notificacion/{id}` carga el recurso desde el backend antes de redirigir, por lo que no confía en parámetros externos para decidir el destino.
- Solo se aceptan deep links `techstore://` con recurso conocido e ID numérico.
- Home muestra badges reales para mensajes y notificaciones y exige sesión antes de abrirlos.
- El badge nativo refleja el total de notificaciones no leídas cuando la plataforma lo soporta.

## Evidencia de validación

| Control | Resultado |
| --- | --- |
| Backend compile | 318 fuentes Java compiladas correctamente. |
| Backend unitario | 315/315 pruebas aprobadas. |
| Migraciones PostgreSQL 15 | `MigracionesIT` 15/15; 42 migraciones, constraint `INTERNAL`, unicidad, índices y RLS de V42 comprobados. |
| Frontend web | ESLint y build aprobados; 6 archivos/12 pruebas con un worker. |
| TypeScript móvil | Sin errores. |
| ESLint móvil | Sin errores ni warnings. |
| Jest móvil | 20 suites/47 pruebas aprobadas. |
| Expo Doctor | 20/20 comprobaciones. |
| Export Android | Bundle Hermes Android generado correctamente; archivo principal de 5,4 MB. |
| Puertos reservados | No quedan referencias activas a `8080` ni `8181`; la API usa `8090`. |

Vitest 4 no acepta `--runInBand`; la suite se ejecutó correctamente con su opción soportada `--maxWorkers=1`.

## Archivos principales de esta fase

- `techstore-backend/src/main/resources/db/migration/V42__centro_notificaciones.sql`
- `techstore-backend/src/main/java/com/techstore/notificaciones/NotificacionUsuario*.java`
- `techstore-backend/src/main/java/com/techstore/notificaciones/outbox/OutboxChannel.java`
- `techstore-backend/src/main/java/com/techstore/notificaciones/outbox/NotificationOutboxPublisher.java`
- `techstore-backend/src/main/java/com/techstore/notificaciones/outbox/NotificationOutboxDispatcher.java`
- `techstore-backend/src/main/java/com/techstore/mensajeria/UnreadMessageCountResponse.java`
- `techstore-backend/src/main/java/com/techstore/mensajeria/{ConversacionController,MensajeRepository,MensajeriaService}.java`
- `techstore-backend/src/test/java/com/techstore/notificaciones/NotificacionUsuarioServiceTest.java`
- `techstore-backend/src/test/java/com/techstore/integracion/MigracionesIT.java`
- `techstore-mobile/src/api/notificationSchemas.ts`
- `techstore-mobile/src/features/notifications/services/notificationCenterService.ts`
- `techstore-mobile/src/features/notifications/hooks/{useNotificationCenter,useHeaderBadges}.ts`
- `techstore-mobile/src/features/notifications/screens/NotificationsScreen.tsx`
- `techstore-mobile/src/app/notifications/index.tsx`
- `techstore-mobile/src/app/notificacion/[id].tsx`

## Nueva migración

- `V42__centro_notificaciones.sql`

## Pendientes

- Fase 12: biometría opcional y hardening conforme a OWASP MASVS.
- SMTP y push reales continúan dependiendo de credenciales externas; el centro interno funciona sin ellas.
- El APK post-Fase 8 queda solo como artefacto histórico porque apuntaba al puerto ahora reservado. El próximo APK de Fase 15 usará `8090`.

## Criterios de aceptación

- [x] Centro interno durable alimentado desde el outbox.
- [x] Listado, lectura individual y lectura masiva.
- [x] Badges de mensajes y notificaciones calculados por backend.
- [x] Deep links allowlist y recurso propio validado antes de navegar.
- [x] Idempotencia, tenant seguro y RLS preservados.
- [x] Backend, web, móvil, Doctor, export y migraciones en verde.
