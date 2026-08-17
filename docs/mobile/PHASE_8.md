# Fase 8 — Mensajería REST, WebSocket/STOMP y Admin Inbox

**Estado:** completada y validada  
**Fecha de cierre:** 2026-08-12  
**Alcance:** conversaciones Cliente ↔ TechStore recuperables, chat en tiempo real y bandeja administrativa.

## Resultado

El cliente autenticado puede iniciar una consulta general o asociada a un pedido propio, recuperar su historial paginado, enviar mensajes y recibir respuestas en tiempo real. Admin y Cajero disponen de un inbox web común con no leídos, conversación activa, respuesta y cierre. REST conserva la fuente de verdad y permite recuperar el chat luego de una desconexión; STOMP reduce la latencia sin reemplazar la persistencia.

## Entregado

- Migración `V39__conversaciones_y_mensajes.sql` con `conversaciones`, `mensajes`, índices, unicidad idempotente y Row Level Security.
- Conversaciones aisladas por tenant, cliente autenticado y pedido propio opcional.
- Endpoints REST para crear/reutilizar, listar, abrir, paginar historial, enviar, marcar lectura y cerrar.
- `clientMessageId` único por remitente: un reintento idéntico devuelve el mensaje existente y uno conflictivo se rechaza.
- Límite persistido de 30 mensajes por remitente/minuto y contenido de 1 a 2.000 caracteres.
- Endpoint WebSocket real `/ws`, destinos de aplicación `/app/chat/{conversationId}` y colas privadas bajo `/user/queue/*`.
- Autenticación del frame STOMP `CONNECT` mediante access token JWT firmado; refresh tokens, cuentas inactivas y conexiones anónimas se rechazan.
- Propagación del tenant firmado a cada ejecución del canal y limpieza posterior del contexto.
- Lista blanca de destinos `SEND`/`SUBSCRIBE`, frames de hasta 16 KiB, buffer de 64 KiB, timeout de envío y heartbeat de 10 segundos.
- Publicación posterior al commit para no emitir mensajes que luego sean revertidos.
- Reconexión automática con recuperación REST en móvil y web.
- Pantallas móviles Mensajes, Nueva conversación y Chat, con acceso desde Mi cuenta y desde el detalle del pedido.
- Historial móvil, pendientes locales, reintento por REST, deduplicación por ID/clientMessageId, no leídos y confirmación de lectura.
- Admin Inbox para roles Admin/Cajero, con lista ordenada por actividad, contador, estado en vivo, conversación y cierre.
- OpenAPI móvil 1.6 y tipos generados para todos los contratos REST de mensajería.

## Seguridad

- El cliente nunca decide `tenantId`, remitente, rol o destinatarios; el backend los obtiene del JWT y la base.
- Conocer un ID de conversación o pedido no concede acceso.
- Las suscripciones permitidas son exclusivamente las colas privadas del usuario; no hay canal global de conversaciones.
- No se transmite el JWT en la URL: viaja en el header STOMP `CONNECT`.
- Los DTO y esquemas Zod estrictos rechazan campos internos como tenant o secretos.
- No se incorporaron adjuntos; por lo tanto no se solicitan permisos de cámara, micrófono o almacenamiento.

## Evidencia de validación

| Control | Resultado |
| --- | --- |
| Backend unitario | 300/300 pruebas aprobadas. |
| Integración catálogo/migraciones | 2/2; PostgreSQL 15 y 39 migraciones validadas. |
| Integración mensajería real | 1/1; Cliente REST/STOMP → Admin, Admin STOMP → Cliente y evento de lectura. |
| TypeScript móvil | Sin errores. |
| ESLint móvil | Sin errores ni warnings. |
| Jest móvil | 17 suites/33 pruebas aprobadas. |
| Frontend web | 6 suites/12 pruebas, lint y build aprobados. |
| Expo Doctor | 20/20 comprobaciones aprobadas. |
| Export web móvil | 33 rutas estáticas, incluidas las tres rutas de mensajería. |
| Acceso directo en vivo | HTTP 200 en `localhost:8081`; proceso preservado. |

## Recuperación y límites declarados

- La entrega en tiempo real usa el simple broker en memoria de Spring y es apropiada para una instancia. Para varias réplicas se requerirá un broker compartido.
- REST es la recuperación canónica: al reconectar se invalidan inbox e historial y se deduplican eventos.
- No hay mensajes con imágenes en esta fase; el tipo existe en dominio, pero la API pública solo acepta texto.
- Las notificaciones push pertenecen a la Fase 9 y no se implementaron durante este checkpoint.

## Criterios de aceptación

- [x] Conversaciones e historial son persistentes y paginados.
- [x] Cliente y staff solo acceden a conversaciones autorizadas.
- [x] STOMP usa JWT, tenant firmado, destinos acotados, heartbeat y reconexión.
- [x] El chat funciona en ambos sentidos y emite lectura en una integración real.
- [x] Admin/Cajero disponen de inbox web en tiempo real.
- [x] REST recupera el estado ante desconexión o frame perdido.
- [x] Backend, web, móvil, Doctor, migraciones y export están en verde.

Antes de Fase 9 se ejecuta el checkpoint obligatorio `PREVIEW_APK.md` para validación en un Xiaomi Redmi Note 8 Pro.
