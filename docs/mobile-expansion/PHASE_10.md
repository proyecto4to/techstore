# FASE 10 — OFFLINE + SINCRONIZACIÓN

ESTADO: COMPLETED

## Decisión de alcance

PostgreSQL y el backend siguen siendo la única fuente de verdad. Mobile no reproduce reglas de negocio ni acepta offline operaciones cuyo resultado dependa de stock, caja, permisos o pagos actuales.

| Área / operación | Clase | Comportamiento |
|---|---|---|
| Catálogo, marcas y detalle de producto | `CACHE` | Red primero; ante fallo de transporte muestra el último dato conocido. TTL online: 15 minutos. |
| Carrito | `OFFLINE` | Estado local por usuario/invitado; se revalida contra backend antes del checkout. |
| Cursor de eventos realtime | `OFFLINE` | Persistencia local por tenant; catch-up REST al reconectar. |
| Mensaje en conversación existente | `OFFLINE_SYNC` | Cola persistente, `clientMessageId` estable, orden FIFO y reintento exponencial. |
| Crear conversación, marcar leído, favoritos, direcciones | `ONLINE_ONLY` | Requiere confirmación del backend; no se simula éxito local. |
| Login, sesión, perfil y recuperación | `ONLINE_ONLY` | La autoridad es Auth/backend. |
| Checkout, pedido, pago, venta, compra, caja y stock | `ONLINE_ONLY` | Reglas transaccionales y disponibilidad se validan centralmente. |
| CRUD administrativo, usuarios, proveedores, uploads y documentos | `ONLINE_ONLY` | Requiere autorización y estado vigente. |
| Notificaciones push | `CACHE` del sistema | FCM puede entregar al reconectar; el listado canónico se consulta por REST. |

## Implementación

- Drift schema 2 agrega `pending_operations` sin destruir `cache_entries`.
- La cola está aislada por `tenant_id` firmado en JWT + email normalizado.
- El mismo UUID viaja en el intento inicial y en todos los reintentos; el backend deduplica por remitente + `clientMessageId`.
- Un fallo de red programa backoff exponencial. Un rechazo de negocio/permiso queda visible y no entra en un ciclo agresivo.
- La cola se procesa al restaurar/login y cuando vuelve la conectividad.
- Banner global informa estado offline y cantidad pendiente.
- La caché vencida solo se lee mediante una API explícita de fallback offline; nunca se confunde con dato vigente.
- Realtime conserva catch-up por cursor y no introduce polling permanente.

## Resolución de conflictos

- Mensajería: identidad idempotente; el servidor es autoridad y una confirmación elimina la operación local.
- Carrito: el backend recalcula precio/disponibilidad antes de crear el pedido; un conflicto exige decisión del usuario.
- Catálogo: estrategia server-wins al revalidar; la copia local es exclusivamente de lectura.
- Operaciones transaccionales: no se encolan, por lo que no pueden duplicar ventas, stock, pagos o movimientos de caja.

## Pruebas y aceptación

- [x] Persistencia y aislamiento de caché.
- [x] Lectura vigente rechaza expirados y fallback conserva último dato.
- [x] Falla de red en chat crea una sola operación persistente.
- [x] Reconexión reenvía exactamente el mismo `clientMessageId` y elimina tras éxito.
- [x] Scope local extrae y aísla `tenant_id`.
- [x] Flutter analyze sin incidencias.
- [x] Flutter tests: 16/16.
- [x] APK Development debug compilado con schema 2.

## Archivos principales

- `techstore-mobile/lib/core/database/app_database.dart`
- `techstore-mobile/lib/core/offline/offline_queue_service.dart`
- `techstore-mobile/lib/app/offline_coordinator.dart`
- `techstore-mobile/lib/app/connectivity_banner.dart`
- `techstore-mobile/lib/features/catalog/data/catalog_repository.dart`
- `techstore-mobile/test/core/offline_queue_service_test.dart`
