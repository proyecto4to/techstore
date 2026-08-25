# FASE 01 — MAPA FUNCIONAL

ESTADO: COMPLETED

## Convenciones

- `FULL`: la función debe existir de punta a punta en Flutter.
- `VIEW`: consulta/descarga útil en móvil; administración compleja permanece Web.
- `N/A`: no corresponde a una app Android (operación de infraestructura).
- `RT`: evento en tiempo real requerido.
- `INV`: invalidación/refetch al volver a foco o tras una mutación.
- `PUSH`: notificación fuera de la app.
- Prioridad `P0`: crítica; `P1`: alta; `P2`: complementaria; `P3`: infraestructura.

## Alcance por rol

| Rol | Alcance Mobile |
|---|---|
| Público | Catálogo, detalle, carrito y registro/login. |
| Cliente | Compra, direcciones, envíos, pedidos, facturas, favoritos, notificaciones, chat y cuenta. |
| Cajero | Venta directa, caja, pedidos, pagos/facturas, consultas de stock, compras/proveedores y reportes operativos. |
| Admin | Todo lo anterior más productos, usuarios, empresa, auditoría, correo, contabilidad y configuración operativa. |

## Inventario funcional detallado

| ID | Nombre | Descripción | Módulo | Web | Mobile | Backend/API | Database | Roles/permisos | Dependencias | Prioridad | Estado |
|---|---|---|---|---|---|---|---|---|---|---|---|
| AUTH-01 | Registro | Alta de cuenta Cliente con validación | usuarios | Sí | FULL | `POST /auth/registro` | usuarios, roles, clientes | Público | JWT, BCrypt | P0 | Existente backend/Web |
| AUTH-02 | Login | Autenticar y emitir access + refresh | usuarios | Sí | FULL | `POST /auth/login` | usuarios, intento_login, sesiones_refresh | Público | JWT, rate limit | P0 | Existente backend/Web |
| AUTH-03 | Rotación de sesión | Renovar tokens y revocar refresh anterior | usuarios | Sí | FULL | `POST /auth/refresh` | sesiones_refresh | Sesión válida | JWT | P0 | Existente backend/Web |
| AUTH-04 | Logout | Revocar sesión actual | usuarios | Sí | FULL | `POST /auth/logout` | sesiones_refresh | Autenticado | Secure storage Mobile | P0 | Existente backend/Web |
| AUTH-05 | Mi perfil | Consultar usuario/rol/tenant | usuarios | Sí | FULL | `GET /auth/me` | usuarios, roles | Autenticado | JWT | P0 | Existente backend/Web |
| AUTH-06 | Cambio de contraseña | Validar actual y reemplazar hash | usuarios | Sí | FULL | `PUT /auth/password` | usuarios, sesiones_refresh | Autenticado | BCrypt | P0 | Existente backend/Web |
| AUTH-07 | Datos fiscales cliente | Actualizar RUC/razón social/datos de cuenta | usuarios | Sí | FULL | `PUT /auth/datos-fiscales` | usuarios, clientes | Cliente | Validadores PY | P1 | Existente backend/Web |
| USER-01 | Gestión de usuarios | Listar, crear y editar usuarios/roles | usuarios | Sí | FULL | `/usuarios` | usuarios, roles | Admin | Auditoría | P1 | Existente backend/Web |
| USER-02 | Activar/desactivar | Bloquear o rehabilitar una cuenta | usuarios | Sí | FULL | `/usuarios/{id}/activo` | usuarios, sesiones_refresh | Admin | Auth | P1 | Existente backend/Web |
| USER-03 | Reset de contraseña | Establecer clave administrativa | usuarios | Sí | FULL | `/usuarios/{id}/password` | usuarios, sesiones_refresh | Admin | BCrypt/auditoría | P1 | Existente backend/Web |
| USER-04 | Punto de expedición | Asignar caja/punto fiscal al usuario | usuarios/empresa | Sí | FULL | `/usuarios/{id}/punto-expedicion` | usuarios, puntos_expedicion | Admin | Empresa | P1 | Existente backend/Web |
| CAT-01 | Catálogo | Buscar, filtrar y paginar productos visibles | catálogo | Sí | FULL | `GET /productos` | productos, marcas, modelos | Público | Imágenes/API | P0 | Existente backend/Web |
| CAT-02 | Detalle de producto | Ver precio, moneda, IVA, imagen y disponibilidad | catálogo | Sí | FULL | `GET /productos/{id}` | productos, marcas, modelos | Público | DTO público | P0 | Existente backend/Web |
| CAT-03 | Marcas | Listar marcas activas | catálogo | Sí | FULL | `GET /marcas` | marcas | Público | Catálogo | P1 | Existente backend/Web |
| CAT-04 | Favoritos | Listar/agregar/quitar favoritos propios | catálogo | No visible en Web actual | FULL | `/favoritos` | favoritos, productos, usuarios | Cliente | Auth | P1 | Backend/API existente |
| CAT-05 | Admin productos | Listar internos, crear, editar y baja lógica | catálogo | Sí | FULL | `/productos/admin`, CRUD | productos, marcas, modelos | Admin | Auditoría | P0 | Existente backend/Web |
| CAT-06 | Foto de producto | Cámara/galería, compresión y upload | catálogo | Archivo local | FULL nativo | `POST /productos/{id}/imagen` | productos + storage | Admin | Cámara/galería | P1 | Backend/Web existente; Mobile pendiente |
| CAT-07 | Reposición | Stock bajo, punto sugerido e historial | catálogo | Sí | FULL | `GET /productos/reposicion` | productos, pedido_detalles | Admin/Cajero | Ventas/compras | P1 | Existente backend/Web |
| CART-01 | Carrito local | Agregar, modificar, quitar y persistir items | ventas | Sí | FULL | Cliente local | caché local | Público/Cliente | Catálogo | P0 | Web existente |
| CART-02 | Revalidar carrito | Recalcular precio/disponibilidad sin reservar | ventas | Sí | FULL | `POST /carrito/validar` | productos/pedidos | Público | Stock backend | P0 | Existente backend/Web |
| ADDR-01 | Direcciones | CRUD de direcciones propias | direcciones | Parcial en checkout/cuenta | FULL | `/direcciones` | direcciones_entrega | Cliente | GPS opcional | P0 | Backend/API existente |
| SHIP-01 | Cotizar envío | Calcular opciones vigentes por peso/dirección | envíos | Sí | FULL | `POST /envios/cotizar` | tarifas_envio, productos | Cliente | Direcciones/carrito | P0 | Existente backend/Web |
| SHIP-02 | Tracking cliente | Consultar envío y eventos de un pedido | envíos | Sí | FULL | `GET /envios/pedidos/{id}` | envios, envio_eventos | Dueño/Admin/Cajero | Pedidos | P1 | Existente backend/Web |
| SHIP-03 | Tarifas de envío | CRUD/activación de tarifas | envíos | Backend sin pantalla dedicada completa | FULL Admin | `/envios/tarifas` | tarifas_envio | Admin | Empresa | P1 | Backend existente |
| SHIP-04 | Estado de envío | Crear/actualizar estado y registrar evento | envíos | Operación administrativa | FULL | `/envios` | envios, envio_eventos | Admin/Cajero | Pedidos/notificaciones | P1 | Backend existente |
| SALE-01 | Checkout | Crear pedido idempotente y comprometer stock | ventas | Sí | FULL | `POST /pedidos` | pedidos, detalles, productos | Cliente | Carrito/dirección/envío | P0 | Existente backend/Web |
| SALE-02 | Historial de pedidos | Listar propios o todos según rol | ventas | Sí | FULL | `GET /pedidos` | pedidos | Autenticado | Paginación | P0 | Existente backend/Web |
| SALE-03 | Detalle de pedido | Consultar líneas, totales, estado y envío | ventas | Sí | FULL | `GET /pedidos/{id}` | pedidos, detalles | Dueño/Admin/Cajero | Auth | P0 | Existente backend/Web |
| SALE-04 | Cancelar pedido | Cancelar pendiente y liberar compromiso | ventas | Sí | FULL | `PUT /pedidos/{id}/cancelar` | pedidos, productos | Dueño/Admin | Stock/auditoría | P0 | Existente backend/Web |
| SALE-05 | Confirmar pedido | Confirmar venta y descontar stock real | ventas | Sí | FULL | `PUT /pedidos/{id}/confirmar` | pedidos, productos | Admin/Cajero | Caja/factura | P0 | Existente backend/Web |
| SALE-06 | Venta directa | Registrar items y pagos en mostrador | ventas | Sí | FULL | `POST /ventas` | pedidos, pagos, caja, facturas | Admin/Cajero | Stock/caja | P0 | Existente backend/Web |
| PAY-01 | Pago múltiple | Cobrar con una o varias líneas/medios | facturación | Sí | FULL | `/pagos` | pagos, pedidos, caja | Admin/Cajero | Venta/caja | P0 | Existente backend/Web |
| INV-01 | Factura PDF | Descargar/compartir comprobante | facturación | Sí | FULL nativo | `/facturas/{id}/pdf`, `/pedidos/{id}/factura/pdf` | facturas | Dueño/Admin/Cajero | PDF/share/print | P0 | Existente backend/Web |
| INV-02 | XML/KuDE | Descargar documento electrónico y KuDE | facturación | Sí | VIEW/share | `/facturas/{id}/documento-electronico`, `/kude` | facturas/config | Admin/Cajero | SIFEN demo | P1 | Existente backend/Web |
| INV-03 | Nota de crédito | Devolución total/parcial y actualización stock/caja | facturación | Sí | FULL | `/pedidos/{id}/nota-credito` | notas_credito, detalles | Admin/Cajero | Factura/pagos | P0 | Existente backend/Web |
| INV-04 | Listado de notas | Consultar y descargar PDF | facturación | Sí | FULL | `/notas-credito` | notas_credito | Admin/Cajero | Paginación/PDF | P1 | Existente backend/Web |
| BUY-01 | Proveedores | Listar, crear, editar y activar | compras | Sí | FULL | `/proveedores` | proveedores | Admin/Cajero | Auditoría | P1 | Existente backend/Web |
| BUY-02 | Ofertas proveedor | Mantener productos/precios/plazos | compras | Sí | FULL | `/proveedores/{id}/ofertas` | proveedor_productos | Admin/Cajero | Productos | P1 | Existente backend/Web |
| BUY-03 | Comparar proveedores | Comparar costo y entrega para reposición | compras | Sí | FULL | `POST /compras/comparar` | proveedor_productos | Admin/Cajero | Reposición | P1 | Existente backend/Web |
| BUY-04 | Registrar compra | Crear pedido de compra | compras | Sí | FULL | `POST /compras` | pedidos, detalles | Admin/Cajero | Proveedor | P1 | Existente backend/Web |
| BUY-05 | Recibir compra | Ingresar stock | compras | Sí | FULL | `PUT /compras/{id}/recibir` | pedidos, productos | Admin/Cajero | Stock | P0 | Existente backend/Web |
| BUY-06 | Pagar compra | Registrar egreso/condición | compras | Sí | FULL | `PUT /compras/{id}/pagar` | pedidos, caja | Admin/Cajero | Caja | P1 | Existente backend/Web |
| BUY-07 | Anular compra | Anular según estado válido | compras | Sí | FULL | `PUT /compras/{id}/anular` | pedidos | Admin/Cajero | Reglas backend | P1 | Existente backend/Web |
| BUY-08 | Enviar al proveedor | Correo/WhatsApp/ambos | compras | Sí | FULL/share | `POST /compras/{id}/enviar` | pedidos, correos | Admin/Cajero | SMTP/deep link | P2 | Existente backend/Web |
| BUY-09 | Comprobante proveedor | Cargar/consultar datos del comprobante | compras | Sí | FULL/cámara | `/compras/{id}/comprobante` | compra_comprobantes | Admin/Cajero | Cámara/archivo | P1 | Existente backend/Web |
| CASH-01 | Apertura | Abrir caja con saldo inicial | caja | Sí | FULL | `POST /caja/apertura` | aperturas_caja | Admin/Cajero | Usuario/punto | P0 | Existente backend/Web |
| CASH-02 | Movimientos | Registrar/listar ingresos y egresos | caja | Sí | FULL | `/caja/movimientos` | movimiento_caja | Admin/Cajero | Apertura | P0 | Existente backend/Web |
| CASH-03 | Resumen | Consultar saldo y totales/PDF | caja | Sí | FULL | `/caja/resumen` | caja/pagos | Admin/Cajero | PDF | P1 | Existente backend/Web |
| CASH-04 | Cierre | Preview, cerrar y listar cierres | caja | Sí | FULL | `/caja/cierres` | cierres_caja | Admin/Cajero | Movimientos | P0 | Existente backend/Web |
| ORG-01 | Datos empresa | Ver/editar emisor y pendientes fiscales | empresa | Sí | FULL | `/empresa` | configuracion | Admin | Cifrado/auditoría | P1 | Existente backend/Web |
| ORG-02 | Puntos de expedición | Crear y activar puntos fiscales | empresa | Sí | FULL | `/empresa/puntos-expedicion` | puntos_expedicion | Admin | Usuarios/factura | P1 | Existente backend/Web |
| REP-01 | Dashboard | Indicadores y top productos | reportes | Sí | FULL | `GET /dashboard` | pedidos/productos/caja | Admin/Cajero | Agregados | P1 | Existente backend/Web |
| REP-02 | Reporte ventas | Filtros, datos y PDF | reportes | Sí | FULL/view | `/reportes/ventas` | pedidos/pagos | Admin/Cajero | PDF | P1 | Existente backend/Web |
| REP-03 | Stock bajo | Listado y PDF | reportes | Sí | FULL/view | `/reportes/stock-bajo` | productos | Admin/Cajero | PDF | P1 | Existente backend/Web |
| ACC-01 | Libros IVA | Libro ventas/compras y PDF | contabilidad | Sí | VIEW | `/contabilidad/libro-*` | facturas/compras | Admin | Fiscal/PDF | P2 | Existente backend/Web |
| ACC-02 | Resumen IVA | Liquidación por periodo y PDF | contabilidad | Sí | VIEW | `/contabilidad/resumen-iva` | facturas/compras | Admin | Fiscal/PDF | P2 | Existente backend/Web |
| ACC-03 | Registro DNIT | Generar ZIP RG 90/2021 | contabilidad | Sí | VIEW/share | `/contabilidad/registro-comprobantes` | ventas/compras | Admin | ZIP/storage | P2 | Existente backend/Web |
| MAIL-01 | Bandeja correo | Listar correos y estados | notificaciones | Sí | FULL | `GET /correos` | correos/outbox | Admin/Cajero | SMTP | P2 | Existente backend/Web |
| MAIL-02 | Config SMTP | Ver/editar cifrado y probar envío | notificaciones | Sí | FULL Admin | `/correos/config`, `/prueba` | configuracion | Admin | Encryption key | P2 | Existente backend/Web |
| PUSH-01 | Dispositivo | Registrar/actualizar/eliminar push token | notificaciones | N/A | FULL nativo | `/dispositivos` | dispositivos_moviles | Autenticado | FCM/Push provider | P0 | Backend API existente; Flutter pendiente |
| NOTIF-01 | Centro notificaciones | Listar no leídas y marcar lectura | notificaciones | Parcial | FULL | `/notificaciones` | notificaciones_usuario | Autenticado | Outbox | P0 | Backend API existente |
| CHAT-01 | Conversaciones | Crear/listar/abrir conversación propia | mensajería | Sí | FULL | `/conversaciones` | conversaciones | Autenticado | Pedidos/usuarios | P1 | Existente backend/Web |
| CHAT-02 | Mensajes | Historial, envío idempotente y lectura | mensajería | Sí | FULL | REST + `/app/chat/{id}` | mensajes | Participante/Admin | STOMP | P0 | Existente backend/Web |
| AUD-01 | Auditoría | Filtrar cambios por entidad/usuario/fecha | auditoría | Sí | VIEW Admin | `/auditoria` | audit_log | Admin | Paginación | P2 | Existente backend/Web |
| OPS-01 | Inicio/parada | Operar stack local/comercial | operaciones | Scripts | N/A | PowerShell/Docker | procesos | Operador host | Java/Node/Postgres | P3 | Existente |
| OPS-02 | Backup/restore | Respaldar/restaurar PostgreSQL y uploads | operaciones | Scripts | N/A | PowerShell/pg tools | DB/storage | Operador host | PostgreSQL | P3 | Existente |
| OPS-03 | Health/metrics | Estado y métricas restringidas | operaciones | Actuator | N/A | `/actuator` | runtime | Health público/Admin | Spring Actuator | P3 | Existente |

## Matriz de cobertura y sincronización

| Funcionalidad | Web | Mobile | Backend | API | Sync objetivo | Test actual / requerido |
|---|---|---|---|---|---|---|
| Auth y sesión | Sí | FULL | Sí | Sí | Estado local seguro + refresh | Unit/IT actuales + Mobile integration |
| Usuarios/roles | Sí | FULL | Sí | Sí | RT `USER_UPDATED` + INV | Unit/IT + Mobile CRUD/E2E |
| Catálogo público | Sí | FULL | Sí | Sí | RT `PRODUCT_*`, `STOCK_UPDATED` | Unit/IT + Widget/E2E |
| Favoritos | No UI | FULL | Sí | Sí | INV después de mutar | Backend unit + Mobile |
| Productos Admin/fotos | Sí | FULL | Sí | Sí | RT producto/stock + upload | Unit/IT + cámara/upload |
| Reposición | Sí | FULL | Sí | Sí | RT stock/compra + INV | Unit + Mobile |
| Carrito/revalidación | Sí | FULL | Sí parcial | Sí | Caché local + revalidación | Unit Web/backend + Mobile |
| Direcciones | Parcial | FULL | Sí | Sí | INV | Backend unit + Mobile CRUD |
| Envíos/tarifas/tracking | Sí/parcial | FULL | Sí | Sí | RT `SHIPMENT_UPDATED` + PUSH | Backend unit + Mobile/E2E |
| Checkout/pedidos | Sí | FULL | Sí | Sí | RT `ORDER_*`, `STOCK_UPDATED` + PUSH | IT actual + Mobile/E2E |
| Venta directa | Sí | FULL | Sí | Sí | RT venta/stock/caja | Unit/IT + Mobile/E2E |
| Pagos/facturas | Sí | FULL | Sí | Sí | RT pago/factura + PUSH | Unit/IT + Mobile/PDF |
| Notas de crédito | Sí | FULL | Sí | Sí | RT devolución/stock/caja | Unit + Mobile/E2E |
| Proveedores/ofertas | Sí | FULL | Sí | Sí | RT `SUPPLIER_UPDATED` + INV | Unit + Mobile CRUD |
| Compras | Sí | FULL | Sí | Sí | RT compra/stock/caja | Unit + Mobile/E2E |
| Caja | Sí | FULL | Sí | Sí | RT `CASH_UPDATED` | Unit + Mobile/E2E |
| Empresa/puntos | Sí | FULL | Sí | Sí | RT `COMPANY_UPDATED` + INV | Unit + Mobile CRUD |
| Dashboard/reportes | Sí | FULL/VIEW | Sí | Sí | INV por eventos de origen | Unit + Mobile widget |
| Contabilidad/ZIP | Sí | VIEW | Sí | Sí | INV por venta/compra | Unit + descarga/share |
| Correo/SMTP | Sí | FULL Admin | Sí | Sí | RT/outbox status + INV | Unit + Mobile Admin |
| Push/dispositivos | No | FULL | Sí | Sí | PUSH | Unit existente + dispositivo real |
| Centro notificaciones | Parcial | FULL | Sí | Sí | RT/PUSH + INV | Unit + Mobile widget/E2E |
| Chat | Sí | FULL | Sí | Sí/STOMP | RT existente + PUSH fallback | Unit/IT + Mobile realtime |
| Auditoría | Sí | VIEW | Sí | Sí | INV | Backend + Mobile view |
| Backup/restore/host | Scripts | N/A | N/A | N/A | Ninguno | Procedimiento host |

## Dependencias transversales

- Auth/JWT/tenant condicionan toda operación privada.
- Productos/stock alimentan carrito, checkout, ventas, compras, reposición y reportes.
- Pedidos conectan pagos, facturas, envíos, chat, notificaciones y contabilidad.
- Outbox conecta transacciones con correo/push sin bloquear la operación comercial.
- Realtime debe publicar después del commit y aislar por tenant/usuario/rol.
- Offline nunca sustituye validaciones de backend; mutaciones críticas requieren idempotencia.

## Criterios de aceptación

- [x] Funciones existentes inventariadas por módulo, rol, API, DB y dependencia.
- [x] Alcance Mobile explícito para cada función.
- [x] Matriz Web | Mobile | Backend | API | Sync | Test creada.
- [x] Funciones de infraestructura separadas de la UX Android.
- [x] Verificación final sin filas ambiguas y documentación de estado actualizada.

## Verificación

- 67 funciones inventariadas.
- 25 grupos en la matriz de cobertura.
- 14/14 dominios backend cubiertos.
- 0 marcadores ambiguos (`TBD`, `POR DEFINIR`, `??`).
- Frontend revalidado después de los cambios: lint, 12 tests, build y audit exitosos.

PHASE_01 = COMPLETED
