# Fase 10 — Transactional Outbox, correo, push y reintentos

**Estado:** completada y validada  
**Fecha de cierre:** 2026-08-13  
**Alcance:** eventos de negocio durables y entrega asíncrona, aislada por canal y tenant.

## Resultado

Checkout, confirmación/cancelación, cobro, cambios de envío y mensajes ya no llaman a SMTP ni a Expo dentro de la transacción comercial. La misma transacción que modifica el negocio inserta uno o más trabajos en `notification_outbox`; un worker posterior entrega correo y push por separado.

Una caída de SMTP, Expo o la red no revierte el pedido, el pago ni el mensaje. El trabajo queda pendiente con backoff exponencial o termina en `EXHAUSTED` después del límite configurado.

## Eventos conectados

- `PEDIDO_CREADO`
- `PEDIDO_CONFIRMADO`
- `PEDIDO_CANCELADO`
- `PAGO_CONFIRMADO`
- `PEDIDO_PREPARADO`
- `ENVIO_DESPACHADO`
- `ENVIO_EN_CAMINO`
- `ENVIO_ENTREGADO`
- `MENSAJE_NUEVO`

Cada destinatario y canal tiene una clave idempotente propia. El correo y el push pueden reintentar independientemente; una venta de mostrador sin correo explícito no notifica por error al cajero.

## Persistencia y aislamiento

La migración `V41__notification_outbox.sql` crea:

- payload `JSONB` con referencias mínimas, nunca correo, texto del chat, token o secreto;
- estados `PENDING`, `PROCESSING`, `PROCESSED` y `EXHAUSTED`;
- intentos, próximo intento, reclamo, procesamiento y error sanitizado;
- unicidad `(tenant_id, idempotency_key)`;
- índices parciales para trabajos vencidos y reclamos abandonados;
- RLS forzada, política de tenant y política de mantenimiento.

El alta se hace mediante `INSERT ... ON CONFLICT DO NOTHING` dentro de la transacción del servicio de negocio. Esto evita que un reintento idempotente cree entregas duplicadas.

## Worker y reintentos

- Reclamo en lotes con `FOR UPDATE SKIP LOCKED`, apto para más de una instancia.
- Recuperación de `PROCESSING` abandonado después del umbral configurado.
- Transacción corta para reclamar; nunca mantiene locks durante SMTP o HTTP.
- Backoff exponencial: 30, 60, 120, 240 segundos, limitado por configuración.
- Cinco intentos por defecto; luego queda `EXHAUSTED` para inspección.
- `last_error_sanitized` elimina saltos de línea y redacta bearer, password, token, secret y API key; máximo 500 caracteres.
- Logs estructurados por ID, evento, canal e intento.

## Entrega por canal

### Correo

Se reutilizan `CorreoService`, `EmailTemplateService`, SMTP y la generación de KuDE existentes. Se agregaron plantillas de estado de pedido, envío y mensaje. Un registro `ENVIADO` o `SIMULADO` con referencia `OUTBOX-{id}` evita repetir un correo si el worker alcanzó a enviarlo pero todavía no había marcado el evento.

### Push

Se reutiliza `PushNotificationService` de Fase 9. El payload incorpora `eventId`, `eventType`, referencia del agregado y deep link allowlist:

- pedidos, pagos y envíos: `techstore://pedido/{id}`;
- mensajes: `techstore://chat/{id}`.

`PROVIDER_DISABLED` es éxito operativo en local; fallos transitorios vuelven a la cola y tokens inválidos permanentes se desactivan.

## Configuración

```properties
NOTIFICATION_OUTBOX_ENABLED=true
NOTIFICATION_OUTBOX_POLL_INTERVAL_MS=5000
NOTIFICATION_OUTBOX_INITIAL_DELAY_MS=10000
NOTIFICATION_OUTBOX_BATCH_SIZE=20
NOTIFICATION_OUTBOX_MAX_ATTEMPTS=5
NOTIFICATION_OUTBOX_BASE_RETRY_SECONDS=30
NOTIFICATION_OUTBOX_MAX_RETRY_SECONDS=21600
NOTIFICATION_OUTBOX_STALE_AFTER_SECONDS=900
```

## Evidencia de validación

| Control | Resultado |
| --- | --- |
| Backend compile | 310 fuentes Java compiladas correctamente. |
| Backend unitario | 311/311 pruebas aprobadas; 5 nuevas del outbox. |
| Migraciones PostgreSQL 15 | 41 migraciones; `MigracionesIT` 14/14, incluyendo JSONB, unicidad, índices y RLS de V41. |
| Frontend web | ESLint y build aprobados; 6 archivos/12 pruebas con un worker. |
| TypeScript móvil | Sin errores. |
| ESLint móvil | Sin errores ni warnings. |
| Jest móvil | 18 suites/42 pruebas. |
| Expo Doctor | 20/20 comprobaciones. |
| Export Android | Metro completó 2.148 módulos y generó bundle Hermes. |
| Higiene del diff | `git diff --check` sin errores; solo advertencias CRLF preexistentes de Windows. |

El primer Vitest web lanzado en paralelo agotó workers del equipo; la repetición aislada con `--pool=threads --maxWorkers=1` aprobó las 12 pruebas.

## Archivos principales de esta fase

- `techstore-backend/src/main/resources/db/migration/V41__notification_outbox.sql`
- `techstore-backend/src/main/java/com/techstore/notificaciones/outbox/`
- `techstore-backend/src/main/java/com/techstore/notificaciones/TipoCorreo.java`
- `techstore-backend/src/main/java/com/techstore/ventas/PedidoService.java`
- `techstore-backend/src/main/java/com/techstore/facturacion/PagoService.java`
- `techstore-backend/src/main/java/com/techstore/envios/EnvioService.java`
- `techstore-backend/src/main/java/com/techstore/mensajeria/MensajeriaService.java`
- `techstore-backend/src/test/java/com/techstore/notificaciones/outbox/`
- `techstore-backend/src/test/java/com/techstore/integracion/MigracionesIT.java`
- `.env.example`

## Pendientes

- El centro persistente y sus badges se implementan en Fase 11 usando los mismos eventos.
- La entrega externa real sigue requiriendo SMTP configurado y `PUSH_PROVIDER=expo` con credenciales FCM de EAS; en local queda simulada/omitida de forma segura.
- El APK post-Fase 8 no contiene Fases 9 ni 10; un nuevo binario corresponde al checkpoint de Fase 15.

## Criterios de aceptación

- [x] Checkout y pago no dependen de SMTP/push.
- [x] Eventos y trabajos se confirman o revierten con la transacción comercial.
- [x] Email y push reintentan por separado con límite y backoff.
- [x] Enqueue idempotente y correo terminal deduplicado.
- [x] Payload sin secretos ni contenido privado.
- [x] Worker multiinstancia y recuperación de reclamos abandonados.
- [x] Tenant Hibernate/JDBC y PostgreSQL RLS preservados.
- [x] Backend, web, móvil, Doctor, export y migraciones en verde.
