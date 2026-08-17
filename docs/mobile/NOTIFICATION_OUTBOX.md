# Operación del Transactional Outbox

## Flujo

1. El servicio modifica pedido, pago, envío o mensaje.
2. En la misma transacción inserta trabajos por destinatario y canal.
3. El worker reclama un lote sin esperar a otros nodos.
4. Fija explícitamente el tenant antes de cargar datos y entregar.
5. Marca `PROCESSED`, programa el siguiente intento o marca `EXHAUSTED`.

## Diagnóstico

Consulta por tenant desde una conexión con `app.tenant_id` configurado:

```sql
SELECT id, event_type, consumer_channel, aggregate_type, aggregate_id,
       status, attempts, next_attempt_at, processed_at, last_error_sanitized
FROM notification_outbox
ORDER BY created_at DESC;
```

Los estados que requieren atención son:

- `PENDING` con `next_attempt_at` muy antiguo: worker detenido o base inaccesible;
- `PROCESSING` más antiguo que `NOTIFICATION_OUTBOX_STALE_AFTER_SECONDS`: será recuperado automáticamente;
- `EXHAUSTED`: proveedor o datos del agregado necesitan revisión manual.

No copiar `payload` ni errores completos a canales públicos. Aunque el esquema restringe el payload a referencias, los metadatos operativos siguen perteneciendo al tenant.

## Configuración segura

- Mantener el worker habilitado en producción.
- Ajustar el lote y la frecuencia de forma gradual; SMTP y Expo aplican sus propios límites.
- No colocar credenciales SMTP/Expo en el payload ni en variables `EXPO_PUBLIC_*`.
- No cambiar un evento agotado a pendiente sin corregir antes la causa.
- Para mantenimiento multitenant, ejecutar sin `app.tenant_id` solo con el rol dueño autorizado por la política `mantenimiento`.

## Semántica de entrega

La cola ofrece entrega al menos una vez. La clave idempotente evita duplicar trabajos y los correos terminales se reconocen por `OUTBOX-{id}`. Para push, `eventId` permite reconocer reentregas; un proceso puede caer después de que el proveedor aceptó el mensaje y antes de marcarlo, por lo que el cliente debe tratar la navegación como idempotente y volver a consultar REST.
