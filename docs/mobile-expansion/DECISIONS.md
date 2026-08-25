# Registro de decisiones arquitectónicas

## ADR-001 — Flutter dentro del monorepo

Estado: ACEPTADA

- Decisión: crear `techstore-mobile/` en el repositorio TechStore.
- Motivo: contrato, CI, documentación y evolución Web/backend/Mobile quedan atómicos.
- Consecuencia: la app Expo externa se trata como legado independiente; no se modifica.

## ADR-002 — Backend y PostgreSQL únicos

Estado: ACEPTADA

- Mobile no tendrá backend ni DB central paralelos.
- Drift es cache/cola, no fuente de verdad.

## ADR-003 — REST autoritativo + STOMP para eventos

Estado: ACEPTADA

- REST entrega snapshots y confirma comandos.
- STOMP notifica cambios y provoca invalidación/refetch.
- Se reutiliza infraestructura existente y se evita duplicar datos completos en mensajes.

## ADR-004 — Transactional outbox para eventos de dominio

Estado: ACEPTADA

- Eventos realtime se persisten en la misma transacción comercial.
- Dispatcher reintenta y evita pérdida entre commit y publicación.

## ADR-005 — Riverpod, Dio, go_router y Drift

Estado: ACEPTADA

- Riverpod: estado/DI testeable.
- Dio: interceptors/refresh/cancelación.
- go_router: guards y deep links.
- Drift: cache y outbox tipadas/migrables.

## ADR-006 — FCM para push Flutter

Estado: ACEPTADA

- Flutter Android usa Firebase Cloud Messaging mediante la abstracción backend `PushProvider`.
- Expo Push queda solo como proveedor legado mientras exista la app Expo.
- `google-services.json` y credenciales de servicio nunca se versionan.

## ADR-007 — Offline selectivo

Estado: ACEPTADA

- Lecturas cacheables, carrito/preferencias offline y cola solo para comandos seguros/idempotentes.
- Pagos, ventas, recepción, confirmación, stock y caja son ONLINE_ONLY.

## ADR-008 — Mobile cubre roles Cliente, Cajero y Admin

Estado: ACEPTADA

- Cliente recibe experiencia completa de compra.
- Cajero/Admin reciben operación útil según la matriz, con vistas móviles específicas.
- Operaciones de host (backup/restore/inicio) quedan fuera de APK.

## ADR-009 — Google Play como actualización primaria

Estado: ACEPTADA

- Play Store gestiona firma/actualización preferentemente.
- Distribución APK directa exige endpoint, hash, firma y consentimiento explícito; no se activa por defecto.

## ADR-010 — SIFEN no bloquea Flutter pero limita Release fiscal

Estado: ACEPTADA

- Se conserva generación demostrativa.
- No se afirma validez fiscal real sin certificado, timbrado, transmisión y homologación externos.

## ADR-011 — Distribución privada directa antes de Google Play

Estado: ACEPTADA

- La entrega vigente usa el flavor `private` y el package `py.com.techstore.app.private`.
- El APK se firma con un keystore privado externo al repositorio y se actualiza con `versionCode` creciente y la misma firma.
- HTTP solo se permite hacia IPv4 RFC1918 en este flavor; staging/production conservan HTTPS obligatorio.
- La base/backend central continúa siendo la fuente de verdad; la PC debe estar accesible en la LAN.
- Google Play, App Links y Firebase production quedan postergados sin bloquear el uso privado.
