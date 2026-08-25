# Arquitectura objetivo — TechStore Web + Mobile Flutter

Estado: APPROVED FOR IMPLEMENTATION  
Fecha: 2026-08-20

## 1. Principios

1. PostgreSQL/backend central son la única fuente de verdad.
2. Web y Flutter usan la misma API y las mismas reglas de negocio.
3. Flutter nunca escribe directamente en PostgreSQL.
4. Los eventos notifican cambios; los clientes vuelven a consultar la API para obtener estado autoritativo.
5. Toda mutación crítica se valida y autoriza en backend.
6. Tenant, rol y propiedad del recurso se derivan de JWT/datos backend, no de headers libres.
7. Mobile se diseña nativo/adaptativo; no usa WebView como aplicación.

## 2. Vista de contexto

```text
┌─────────────────────┐        HTTPS/REST        ┌─────────────────────────┐
│ Web React           │ ───────────────────────▶ │ Spring Boot API         │
│ TanStack/Zustand    │ ◀──── STOMP events ───── │ Auth + business rules   │
└─────────────────────┘                           │ Domain event outbox     │
                                                  │ Notification outbox     │
┌─────────────────────┐        HTTPS/REST        │ Storage facade          │
│ Flutter Android     │ ───────────────────────▶ └───────────┬─────────────┘
│ Riverpod + Dio      │ ◀──── STOMP events ─────              │
│ Drift cache/outbox  │ ◀──── FCM push ─────────               │ JDBC
│ Secure Storage      │                                      ▼
└─────────────────────┘                           ┌─────────────────────────┐
                                                  │ PostgreSQL 15           │
                                                  │ RLS multi-tenant        │
                                                  │ Flyway migrations       │
                                                  └─────────────────────────┘
```

## 3. Componentes que se conservan

- Backend modular Spring Boot, servicios y reglas existentes.
- PostgreSQL 15, Flyway y RLS multi-tenant.
- JWT access/refresh rotativo y roles `Admin`, `Cajero`, `Cliente`.
- API REST `/api/v1`, ProblemDetail, PageResponse e i18n.
- Web React/Vite como cliente independiente.
- STOMP/WebSocket existente como transporte realtime.
- Transactional outbox de notificaciones y push.
- Uploads centralizados, PDFs/XML/ZIP y scripts operativos.
- CI/security scan/SBOM/licencias existentes.

## 4. Componentes que se amplían

- OpenAPI: de contrato Mobile parcial a contrato versionado de todos los endpoints Mobile.
- Backend: eventos de dominio post-commit, outbox realtime y versionado de agregados cuando sea necesario.
- STOMP: de chat únicamente a invalidación de productos, stock, pedidos, ventas, compras, caja, usuarios, proveedores, empresa, envíos y notificaciones.
- Web: cliente común de eventos que invalida queries TanStack y refresca UI.
- Notificaciones: FCM para Flutter Android conservando abstracción `PushProvider`.
- CI: jobs Flutter analyze/test/build/security/artifact scan.

## 5. Componentes que se crean

- `techstore-mobile/`: aplicación Flutter dentro del monorepo.
- Cliente REST Flutter tipado y normalización RFC 7807.
- Store seguro de tokens y coordinador de refresh single-flight.
- Cache Drift y cola offline idempotente.
- Cliente STOMP autenticado con reconexión/backoff.
- Registro FCM, deep links y centro de notificaciones.
- Adaptadores de cámara, galería, biometría, QR/barcode, PDF/share/print.
- Flavors `development`, `staging`, `production` sin secretos embebidos.

## 6. Componentes que no se tocan sin necesidad

- Semántica de reglas fiscales, dinero, stock, caja y estados aprobados.
- Historial de migraciones Flyway existente; solo se agregan migraciones nuevas.
- RLS/tenant como defensa en profundidad.
- Scripts de backup/restore y datos reales del usuario.
- App Expo externa: discontinuada por ADR-011. Flutter es el unico cliente movil.

## 7. Arquitectura Flutter

### Estructura feature-first

```text
techstore-mobile/
├─ android/
├─ lib/
│  ├─ app/                 # bootstrap, router, theme, flavors
│  ├─ core/
│  │  ├─ auth/             # token vault, session coordinator
│  │  ├─ database/         # Drift, migrations y DAOs
│  │  ├─ errors/           # ProblemDetail y fallos tipados
│  │  ├─ network/          # Dio, interceptors, connectivity
│  │  ├─ realtime/         # STOMP, envelope y invalidación
│  │  ├─ sync/             # cola, retries, idempotencia
│  │  └─ widgets/          # estados y componentes compartidos
│  └─ features/
│     ├─ auth/
│     ├─ catalog/
│     ├─ cart/
│     ├─ checkout/
│     ├─ orders/
│     ├─ inventory/
│     ├─ sales/
│     ├─ purchases/
│     ├─ cash/
│     ├─ invoices/
│     ├─ shipping/
│     ├─ notifications/
│     ├─ chat/
│     ├─ reports/
│     └─ administration/
├─ test/
├─ integration_test/
└─ tool/
```

### Capas por feature

```text
presentation → application → domain ← data
                                  ↑
                       backend conserva reglas críticas
```

- `presentation`: pantallas, widgets, navegación y estado UI.
- `application`: casos de uso/orquestación y providers Riverpod.
- `domain`: modelos/contratos sin Flutter cuando sea práctico.
- `data`: API, Drift, mappers y repositorios.

### Tecnologías seleccionadas

| Necesidad | Selección |
|---|---|
| Estado/DI | Riverpod con providers testeables |
| Routing/deep links | go_router |
| HTTP | Dio con interceptors y cancelación |
| Modelos JSON | json_serializable/freezed solo donde aporte valor |
| Secure storage | flutter_secure_storage/Android Keystore |
| Base local | Drift/SQLite |
| Conectividad | connectivity_plus + verificación real de backend |
| Realtime | cliente STOMP compatible con Spring |
| Push | firebase_messaging + flutter_local_notifications |
| Biometría | local_auth, como desbloqueo local, nunca como credencial backend |
| Cámara/galería | image_picker/camera según flujo + compresión/recorte |
| QR/barcode | mobile_scanner |
| PDF/share/print | share_plus, printing y visor compatible |
| Background retry | WorkManager con límites Android |

Las versiones exactas se fijan en `pubspec.lock` al crear el proyecto y se validan contra Flutter 3.47.1/Dart 3.13.1 disponible.

## 8. Networking y contrato

- Base URL por flavor mediante `--dart-define` no secreto.
- HTTPS obligatorio fuera de development.
- Timeout, cancelación, retry solo para GET/idempotentes.
- POST crítico se reintenta únicamente con `Idempotency-Key` persistida.
- Refresh single-flight: una sola rotación aunque fallen varias requests simultáneas.
- 401 después de refresh cierra sesión de forma consistente.
- Errores RFC 7807 se mapean a dominio/UI sin mostrar stacktraces.
- OpenAPI es contrato fuente; CI detecta cambios incompatibles.

## 9. Autenticación y autorización

```text
login → access en memoria + refresh en secure storage
      → restaurar sesión → refresh rotativo
      → logout/revocación → limpiar memoria, secure storage y cache sensible
```

- Access token se mantiene en memoria; persistencia mínima necesaria para restauración.
- Refresh token en Keystore-backed secure storage.
- Biometría solo autoriza leer/desbloquear la sesión local o confirmar acciones sensibles.
- Router usa sesión/rol para UX; backend vuelve a autorizar cada request.
- Cache local sensible queda separada por tenant/usuario y se purga al cambiar sesión.

## 10. Tiempo real (REAL-TIME)

### Modelo

- Servicios publican eventos después de commit.
- `domain_event_outbox` garantiza que la transacción y el evento no diverjan.
- Dispatcher envía por STOMP y marca entrega; múltiples instancias podrán usar broker externo en el futuro.
- Evento transporta identidad/versión, no reemplaza la respuesta REST.
- Web invalida TanStack Query; Flutter invalida providers/repositorios y refetch.

### Envelope

```json
{
  "eventId": "uuid",
  "type": "STOCK_UPDATED",
  "aggregateType": "PRODUCT",
  "aggregateId": "123",
  "version": 7,
  "occurredAt": "2026-08-20T22:00:00Z",
  "changedFields": ["available"]
}
```

- El tenant no se confía desde payload del cliente.
- Destinos se autorizan en SUBSCRIBE.
- Clientes ignoran duplicados por `eventId` y eventos viejos por `version`.
- Reconexión usa backoff con jitter y luego refetch completo de vistas activas.

## 11. Cache y offline

### Fuente de verdad

La cache acelera lectura; nunca confirma una venta, pago, recepción, cierre o cambio de stock.

### Clases de operación

| Clase | Ejemplos | Conducta |
|---|---|---|
| ONLINE_ONLY | pago, venta, cierre, confirmar pedido, recibir compra | requiere backend y confirmación |
| CACHE | catálogo, marcas, reportes recientes | lectura stale-while-revalidate |
| OFFLINE | carrito, borradores no comerciales, preferencias | local inmediato |
| OFFLINE_SYNC | favoritos, lectura de notificaciones y operaciones expresamente idempotentes | cola persistente + retry |

- Drift separa cache, metadata de versiones y outbox local.
- Cada item tiene UUID/idempotency key, estado, intentos, error y timestamps.
- Backoff exponencial con jitter; límite y acción manual al agotar.
- Conflictos usan versión/ETag cuando se implemente; operaciones críticas retornan 409 y requieren resolución del usuario.

## 12. Notificaciones

- Eventos persistidos en outbox producen correo, push e interna según política.
- FCM token se registra por usuario/tenant/device identifier.
- Rotación y logout actualizan/revocan token.
- Payload push contiene tipo e ID, no información sensible completa.
- Deep link abre pantalla y consulta datos autorizados a la API.
- Notificación recibida en foreground actualiza centro e invalida datos.

## 13. Storage e imágenes

- Backend conserva storage autoritativo de imágenes/documentos.
- Flutter comprime/recorta para UX; backend valida tipo real, tamaño y vuelve a codificar imágenes.
- Nombres generados por servidor; no se confía en rutas del cliente.
- PDFs/XML/ZIP se descargan a cache temporal y se comparten con APIs Android.
- Ningún certificado, secreto, password o token se embebe en APK.

## 14. Seguridad

- TLS y cleartext deshabilitado en production.
- Network Security Config permite localhost solo en development.
- Logs redactan tokens, passwords, RUC sensible cuando corresponda y payloads de pago.
- Release minificado/obfuscado cuando sea compatible, símbolos conservados fuera del APK.
- Keystore de firma fuera de Git y configuración por secretos CI.
- Dependencias auditadas, SBOM y escaneo de APK/artefactos.
- MASVS como checklist Mobile y ASVS para API/Web.

## 15. Rendimiento

- Paginación incremental y listas virtualizadas.
- Imágenes con thumbnails/cache y límites de memoria.
- Providers de alcance pequeño para minimizar rebuilds.
- Cancelación/debounce de búsqueda.
- Eventos invalidan solo claves afectadas.
- Background sync respeta batería/red y restricciones Android.
- Métricas: startup, frame jank, requests, cache hit, reconexiones y fallos de sync.

## 16. CI/CD

```text
backend: clean verify + SBOM + artifact scan
web: npm ci + lint + test + build + audit + SBOM
flutter: pub get locked + format check + analyze + test + integration smoke
         + build apk/aab --release + dependency/SBOM/artifact scan
```

- CI usa versiones fijadas de Java, Node y Flutter.
- Flavors development/staging pueden compilar sin secretos.
- Production release exige firma, URL HTTPS y configuración push válida.
- Artefactos registran commit, versionName/versionCode, ambiente y checksum SHA-256.

## 17. Despliegue y escalado

- Primera implementación: backend único con simple broker STOMP, suficiente para despliegue actual.
- Si hay múltiples instancias: broker relay (RabbitMQ/ActiveMQ) y dispatcher outbox con locking distribuido.
- PostgreSQL sigue central; backups y migraciones preceden despliegues.
- Actualización Android preferida: Google Play. APK directa solo si se autoriza y se implementa verificación de firma/hash.

## 18. Toolchain verificado

- Flutter 3.47.1 stable y Dart 3.13.1.
- Android SDK en `C:\Android\Sdk`, platform android-36/build-tools 36.0.0.
- Licencias Android aceptadas.
- Android Studio JBR detectado por Flutter.
- Durante Fase 02 no había dispositivo; Fase 16 se cerró después en un Redmi Note 8 Pro físico.
- Falta Visual Studio C++ solo para Windows desktop, fuera del alcance Android.
