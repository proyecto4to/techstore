# Fase 5 — Paraguay, direcciones y checkout idempotente

**Estado:** completada  
**Fecha de cierre:** 2026-08-11  
**Alcance:** localización paraguaya, libreta de direcciones, checkout móvil completo y protección contra pedidos duplicados.

## Resultado

TechStore Mobile permite avanzar desde un carrito revalidado por Dirección → Entrega → Pago → Resumen → Confirmación. El backend vuelve a validar precio, IVA y stock dentro de la transacción, compromete inventario y guarda una copia histórica de la dirección. Cada intento móvil incluye `Idempotency-Key`: si una respuesta se pierde, repetir el mismo contenido devuelve el pedido existente sin volver a comprometer stock.

## Entregado

- Configuración reutilizable `es-PY`, `America/Asuncion` y moneda PYG sin decimales innecesarios.
- Formateadores centralizados de moneda, fecha y fecha/hora, preparados para ampliar idiomas sin duplicar reglas.
- Modelo `DireccionEntrega` por usuario y tenant con destinatario, teléfono, departamento, ciudad/distrito, barrio, líneas, número, referencia, código postal y coordenadas opcionales.
- Teléfonos amigables con entrada local y normalización backend a `+595`/E.164.
- Primera dirección marcada automáticamente como principal; selección, alta, edición y eliminación con ownership derivado de `Authentication`.
- RLS, índices tenant/usuario y única dirección principal por usuario en Flyway V37.
- Checkout móvil en cuatro pasos más éxito, con métodos iniciales de entrega y pago.
- Snapshot de dirección en el pedido para que editar o borrar la libreta no cambie compras históricas.
- Precio, moneda, IVA, total y stock obtenidos/revalidados por el backend; el cliente no envía importes como autoridad.
- Idempotencia persistida por usuario: bloqueo pesimista de la fila de usuario, hash SHA-256 canónico y unicidad `(tenant_id, usuario_id, idempotency_key)`.
- La misma clave con el mismo cuerpo devuelve el pedido existente; la misma clave con otro cuerpo devuelve conflicto 409.
- La app persiste clave+firma del intento en AsyncStorage y solo vacía carrito/checkout después de una respuesta exitosa.
- Mutaciones TanStack sin reintento automático; el único reintento HTTP automático posible es el posterior a refresh 401 y queda protegido por la clave.
- Compatibilidad web: el frontend genera y conserva una clave por firma de intento; clientes anteriores sin campos móviles conservan retiro/pago local por defecto.
- OpenAPI móvil 1.3 y tipos TypeScript regenerados.

## Seguridad y multiempresa

- Usuario y tenant nunca se aceptan desde el JSON; se derivan de la sesión autenticada.
- Las consultas de dirección combinan filtro por usuario con `@TenantId`; PostgreSQL refuerza tenant mediante RLS.
- El checkout bloquea usuario y productos antes de insertar, evitando tanto duplicados idempotentes como sobreventa concurrente.
- No se persisten en el cliente precios, stock, direcciones completas ni tokens junto al intento.
- GPS, código postal y número exacto son opcionales; la app no solicita permiso de ubicación para comprar.
- SIFEN y cálculo fiscal permanecen exclusivamente en backend. Mobile solo muestra tasa/importe recibidos.

## Migración

`V37__direcciones_y_checkout_idempotente.sql` crea `direcciones_entrega`, políticas RLS, índices y las columnas de snapshot, métodos e idempotencia en `pedidos`. No se modificó ninguna migración histórica.

## Evidencia de validación

| Control | Resultado |
| --- | --- |
| Backend unitario | 288 pruebas, 0 fallos/errores. |
| `CatalogoPublicoIT` | 2/2 sobre PostgreSQL 15, Spring/Hibernate y las 37 migraciones. |
| Backend focalizado | 19/19 de pedido, dirección y teléfono paraguayo. |
| TypeScript móvil | Sin errores. |
| ESLint móvil | Sin errores ni warnings. |
| Jest móvil | 12 suites/22 pruebas aprobadas. |
| Expo Doctor | 20/20 comprobaciones aprobadas. |
| Export web móvil | 28 rutas estáticas generadas. |
| Frontend web | ESLint limpio, 5 archivos/10 pruebas y build Vite aprobados. |

La primera integración detectó que el hash SQL estaba declarado `CHAR(64)` mientras JPA esperaba `VARCHAR(64)`. Se corrigió dentro de la nueva V37 antes de aplicarla al entorno en vivo y la repetición con recursos actualizados aprobó 2/2.

## Límites declarados

- `ENVIO_DOMICILIO` queda como coordinación inicial; cotización, zonas y proveedores reales pertenecen a Fase 6.
- `PAGO_EN_LOCAL` y `TRANSFERENCIA_BANCARIA` registran intención, no cobran en la app; proveedores de pago pertenecen a una fase posterior.
- La pantalla Pedidos continúa pendiente de conectarse al historial real en Fase 7.
- No se reinició el backend en vivo durante el desarrollo; la validación de V37 se hizo sobre PostgreSQL embebido limpio para preservar los procesos solicitados por el usuario.

## Criterios de aceptación

- [x] Localización paraguaya centralizada.
- [x] Direcciones propias, multi-tenant y con campos opcionales correctos.
- [x] Flujo Dirección → Entrega → Pago → Resumen → Éxito.
- [x] Revalidación transaccional y snapshot histórico.
- [x] Idempotencia segura frente a timeout, refresh y reintento concurrente.
- [x] Carrito solo se vacía después del éxito.
- [x] Contrato, pruebas, build y Doctor en verde.

La Fase 6 puede sustituir la coordinación inicial por capacidades de envío, zonas, tarifas y tracking sin cambiar la semántica idempotente del checkout.

