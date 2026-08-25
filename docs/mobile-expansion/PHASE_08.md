# FASE 08 — FUNCIONALIDADES DE NEGOCIO

ESTADO: COMPLETED

## Implementación

- Catálogo público con búsqueda, filtro de marca, paginación backend, detalle, imágenes, disponibilidad y favoritos.
- Carrito persistente, revalidación, direcciones CRUD completas, cotización, checkout idempotente, pedidos, cancelación y tracking.
- Cuenta cliente, datos fiscales, cambio de contraseña, facturas, notificaciones y chat.
- Flujos dedicados de venta directa con pagos múltiples, caja y compras/comparación de proveedores.
- Recursos operativos: dashboard, reposición, pedidos, compras, proveedores/ofertas, caja, facturas, notas de crédito, reportes y correo.
- Recursos Admin: usuarios, productos, empresa, puntos de expedición, tarifas, IVA, auditoría y SMTP.
- Mutaciones reales para confirmación/cancelación/pago/devolución, compras, stock, usuarios, productos, proveedores, envíos, empresa y configuración.
- Navegación y acciones protegidas por Cliente, Cajero y Admin; el backend vuelve a autorizar cada operación.
- Invalidation/refetch conectado a eventos de dominio STOMP para catálogo, stock, pedidos, envíos, compras, proveedores, caja, clientes, usuarios y configuración.

## Cobertura del inventario

| Grupo Fase 01 | Estado negocio Mobile |
|---|---|
| AUTH-01…07 | COMPLETED en Fase 07 |
| USER-01…04 | COMPLETED |
| CAT-01…05, CAT-07 | COMPLETED |
| CART-01…02 | COMPLETED |
| ADDR-01, SHIP-01…04 | COMPLETED |
| SALE-01…06, PAY-01 | COMPLETED |
| INV-03…04 | COMPLETED |
| BUY-01…09 | COMPLETED; captura/archivo nativo continúa en Fase 09 |
| CASH-01…04 | COMPLETED |
| ORG-01…02 | COMPLETED |
| REP-01…03, ACC-01…02 | COMPLETED como consulta Mobile |
| MAIL-01…02, NOTIF-01, CHAT-01…02, AUD-01 | COMPLETED |
| CAT-06, INV-01…02, ACC-03, PUSH-01 | Capa negocio/API lista; integración Android corresponde a Fase 09 |
| OPS-01…03 | N/A Mobile según Fase 01 |

## Pruebas

- Flutter `analyze --no-pub`: sin incidencias.
- Flutter tests: 12/12 OK.
- E2E real negocio: catálogo, filtro de marca, detalle, favoritos CRUD, revalidación de carrito y direcciones CRUD OK.
- E2E realtime: evento `USER_UPDATED` recibido por STOMP, tenant aislado y sin polling permanente.
- Backend: 324 unit + 43 integration tests OK; Flyway 44/44.
- Web: lint + 14 tests + build de producción OK.
- Android: `app-development-debug.apk` generado correctamente.

## Criterios de aceptación

- [x] Implementación de negocio Mobile del inventario real.
- [x] Integración con API y reglas backend centrales.
- [x] Roles/permisos aplicados.
- [x] Sincronización/invalidation conectada.
- [x] Pruebas Mobile, Web, backend y realtime aprobadas.
- [x] Sin pendientes críticos propios de la fase.

Las capacidades de cámara, archivos, compartir, biometría y push no se declaran completas aquí: son el alcance explícito de Fase 09.
