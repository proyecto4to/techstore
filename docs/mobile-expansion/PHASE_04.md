# FASE 04 — Tiempo real

ESTADO: COMPLETED

## Resultado

- Outbox de dominio transaccional y multi-tenant agregado mediante Flyway V43.
- Triggers para 21 entidades centrales con eventos CRUD y `STOCK_UPDATED` específico.
- Worker con reclamo `SKIP LOCKED`, recuperación de reclamos abandonados y reintento seguro.
- Entrega STOMP en `/topic/tenant/{tenantId}/events` con JWT y validación estricta del tenant.
- Recuperación durable por cursor en `GET /api/v1/sync/events`.
- Web conectada globalmente con heartbeat, reconexión, deduplicación e invalidación selectiva de React Query.
- El contrato OpenAPI fue regenerado desde el código compilado: 98 rutas y 120 operaciones.

## Criterios de aceptación

- [x] Web → backend → base → evento → clientes conectados.
- [x] Mobile podrá escribir y consumir la misma ruta/flujo sin lógica paralela.
- [x] La UI no necesita cerrarse o reabrirse para revalidar datos.
- [x] Los eventos perdidos durante desconexión se recuperan por cursor.
- [x] Duplicados tolerados mediante cursor e invalidaciones idempotentes.
- [x] Aislamiento de tenant aplicado en base y canal STOMP.
- [x] Pruebas unitarias, integración y Web aprobadas.
- [x] Sin pendientes críticos de fase.

## Pruebas

- Backend: suite unitaria aprobada; worker y autorización STOMP cubiertos.
- Integración completa: 40/40 aprobadas tras V43.
- Regresión dirigida: 18/18 aprobadas, incluida una entidad sin ID numérico, migración, login, cambio real de stock, outbox y recuperación REST.
- Web: 7 archivos, 14 tests, lint y build de producción aprobados.

## Garantías

REST/PostgreSQL siguen siendo la fuente de verdad. Los frames contienen metadata mínima y obligan a reconsultar datos autorizados. La entrega es al menos una vez y no usa polling permanente.
