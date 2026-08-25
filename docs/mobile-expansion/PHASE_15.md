# FASE 15 — TESTING

ESTADO: COMPLETED

## Regresión consolidada — 2026-08-22

| Capa | Verificación | Resultado |
|---|---|---|
| Backend unit | `mvn clean verify` / Surefire | 327/327 OK |
| Backend integration | PostgreSQL 15 embebido, Flyway real / Failsafe | 44/44 OK; 45 migraciones |
| Contrato API | OpenAPI generado desde Spring | 102 rutas / 125 operaciones; OK |
| Web | ESLint | 0 errores y 0 warnings |
| Web | Vitest | 14/14 OK |
| Web | Vite production build | OK; chunk inicial 234,66 kB |
| Flutter | `flutter analyze` | 0 incidencias |
| Flutter | unit/widget | 19/19 OK |
| E2E auth | registro, login, refresh rotativo y logout | OK |
| E2E negocio | catálogo/filtro, favorito CRUD, carrito y dirección CRUD | OK |
| E2E realtime | STOMP `USER_UPDATED` y topic aislado por tenant | OK |

## Cobertura funcional automatizada

- Sesión segura, concurrencia de refresh y expiración.
- Contratos REST, Problem Details y migraciones de base de datos.
- Catálogo, carrito, checkout auxiliar, direcciones, favoritos y modelos de pedidos.
- Eventos de dominio WebSocket/STOMP con aislamiento multi-tenant.
- Cola offline idempotente, reintento con el mismo `clientMessageId` y scope tenant/usuario.
- Política de actualización opcional/obligatoria y utilidades de deep links nativos.
- Routing, restauración de sesión, estados async accesibles y texto al 200%.

## Incidencias encontradas y corregidas

1. El arnés E2E de negocio había heredado `dart:ui` a través de Drift y no arrancaba con Dart CLI. Se desacopló a Dio puro conservando los mismos contratos reales.
2. El primer Vitest no pudo crear workers por contención de procesos en Windows. Se detuvo el daemon Gradle del proyecto y se repitió con un único worker `threads`: 14/14 OK.
3. El outbox de notificaciones permanecía activo durante integración y retrasaba el cierre del JVM. Se deshabilitó solo en el perfil test; la prueba de contexto posterior cerró limpiamente.

## Higiene de datos

- Las cuatro cuentas E2E y sus ocho sesiones refresh fueron eliminadas dentro de una transacción.
- Verificación posterior: 0 usuarios `codex-*@e2e.invalid` residuales.

## Límites explícitos

- Cámara, galería, biometría, permisos, FCM real, background e instalación/actualización Play requieren un Android físico y/o credenciales externas. Se trasladan a Fase 16/20, sin declararlas aprobadas por emulador.
- No se configuró un umbral numérico de cobertura de líneas: la aceptación se basa en capas y escenarios críticos ejecutados, no en un porcentaje artificial.

## Criterios de aceptación

- [x] Unit, widget, integration y E2E consolidados.
- [x] Regresión completa backend/Web/Mobile aprobada.
- [x] Sync, offline, actualización y capacidades nativas automatizables cubiertas.
- [x] Pruebas dependientes de hardware/servicios separadas y documentadas.
- [x] Incidencias corregidas y revalidadas.
- [x] Sin pendientes críticos automatizables.
