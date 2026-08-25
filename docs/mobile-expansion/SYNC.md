# Sincronización Web ↔ Mobile

## Modelo

PostgreSQL y el backend son la única fuente de verdad. Web y Flutter escriben mediante REST; 21 entidades centrales generan eventos en `domain_event_outbox` dentro de la misma transacción. Un worker entrega esos eventos por STOMP después del commit.

```text
Web / Flutter → REST → regla de negocio → PostgreSQL + outbox
                                             ↓
                                  /topic/tenant/{id}/events
                                             ↓
                                  invalidar caché → REST
```

El evento contiene únicamente `id`, tipo, agregado, identificador, metadata mínima y fecha. No replica la entidad ni sustituye una lectura autorizada por REST.

## Seguridad y aislamiento

- STOMP exige access token JWT en `CONNECT`.
- La suscripción `/topic/tenant/{tenantId}/events` se acepta únicamente cuando el tenant firmado coincide con la ruta.
- El outbox usa RLS forzado y dos políticas, igual que el resto del dominio.
- No se incluyen datos personales, credenciales ni documentos completos en un frame.

## Entrega y recuperación

La entrega es al menos una vez: un corte entre el envío y el marcado puede duplicar un frame. Los clientes deduplican por el cursor `id`; las invalidaciones son idempotentes.

Al conectar o reconectar, el cliente:

1. Se suscribe al topic del tenant.
2. Consulta `GET /api/v1/sync/events?after={cursor}&limit=200`.
3. Procesa páginas en orden y luego los frames recibidos durante la recuperación.
4. Persiste el último cursor por tenant.
5. Reconsulta por REST las claves afectadas.

La Web aplica reconexión exponencial de 1 a 15 segundos y heartbeats de 10 segundos. Flutter reutiliza el mismo contrato, persiste el cursor aislado por tenant y coordina la recuperación al reconectar/autenticar.

## Eventos

Se generan `*_CREATED`, `*_UPDATED` y `*_DELETED` para usuarios, clientes, proveedores, catálogo, pedidos/detalles, pagos, facturas, notas de crédito, caja, configuración, direcciones, envíos, tarifas, ofertas, comprobantes y puntos de expedición. Los cambios de existencia generan además `STOCK_UPDATED` con stock real y comprometido.

## Fallback

La recuperación por cursor es el fallback principal, no un polling permanente. Una pantalla puede revalidar al recuperar foco o por acción del usuario; nunca se considera el cache local como una segunda realidad.

## Offline Mobile

La clasificación completa está en `PHASE_10.md`. Solo los mensajes de conversaciones existentes usan cola offline porque el backend ya garantiza idempotencia con `clientMessageId`. La cola Drift queda aislada por tenant y usuario, respeta FIFO y usa backoff exponencial. Catálogo usa red primero con último dato conocido como fallback; carrito permanece local pero checkout, stock, pagos, caja y CRUD siguen siendo online-only.
