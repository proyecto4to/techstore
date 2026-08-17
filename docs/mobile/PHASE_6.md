# Fase 6 — Envíos configurables y tracking

**Estado:** completada  
**Fecha de cierre:** 2026-08-11  
**Alcance:** `ShippingProvider`, tarifas por reglas, `Envio`, `EnvioEvento`, costo fiscal y seguimiento móvil.

## Resultado

El checkout ya no inventa ni fija tarifas en la app. TechStore Mobile envía dirección e items; el backend calcula peso y subtotal vigentes, aplica reglas configurables y devuelve retiro, entrega local, otras ciudades/departamentos o transportadora. Al confirmar, revalida la tarifa dentro de la misma transacción idempotente, incluye el servicio de entrega en el total y crea un envío con su primer evento. El cliente puede consultar su línea temporal desde la confirmación.

## Entregado

- Interfaz extensible `ShippingProvider` y primera implementación `DatabaseShippingProvider`.
- Tarifas por tenant configurables mediante API Admin: tipo, proveedor, transportista, ciudad, departamento, zona, peso, subtotal, costo, IVA, umbral gratis, promoción, vigencia, ETA y prioridad.
- Retiro gratuito y tarifas iniciales editables sembradas en backend; Mobile no contiene precios logísticos.
- Peso unitario en producto y campo web de administración.
- Cotización autenticada `POST /api/v1/envios/cotizar`; el servidor carga productos y calcula subtotal/peso, sin aceptar importes del cliente.
- Selección de tarifa persistida con el intento de checkout e incluida en su firma idempotente.
- Entidades multi-tenant `Envio` y `EnvioEvento`, estados controlados, tracking interno `TS-{numero}` y fechas estimadas/despacho/entrega.
- Transiciones válidas y endpoint Staff para registrar estado, ubicación, transportista y código de seguimiento.
- Tracking por pedido con verificación de ownership; Admin/Cajero pueden consultar cualquiera.
- Confirmar pedido pasa envío a preparación; cancelar pedido cancela el envío cuando todavía corresponde.
- Costo e IVA del envío integrados al total, cobro, factura PDF, XML SIFEN y KuDE como línea de servicio; retiro gratis no agrega una línea fiscal.
- Pantalla móvil de opciones devueltas por backend, costo/ETA/transportista, resumen con total final y ruta `/tracking/[pedidoId]` con `ShippingTimeline`.
- OpenAPI móvil 1.4 y tipos TypeScript regenerados.

## Migración

`V38__envios_tarifas_y_peso.sql` agrega peso de producto, costo/IVA de envío al pedido, `tarifas_envio`, `envios`, `envio_eventos`, índices y políticas RLS. También crea tarifas iniciales por cada tenant existente. No se modificó ninguna migración histórica.

## Seguridad y consistencia

- Usuario y tenant salen de la autenticación y del contexto seguro; nunca del JSON.
- La cotización no recibe precio, peso, IVA ni total como autoridad.
- El checkout vuelve a calcular la elegibilidad de la tarifa después de bloquear los productos.
- Pedido, stock y envío se guardan dentro de la misma transacción; un reintento idempotente devuelve el pedido existente y no duplica logística.
- El costo cobrado coincide con factura, XML y KuDE; existe una regresión fiscal específica.
- Tarifa y tracking respetan `@TenantId` y RLS de PostgreSQL.

## Evidencia de validación

| Control | Resultado |
| --- | --- |
| Backend unitario | 293/293 pruebas aprobadas. |
| `CatalogoPublicoIT` | 2/2 sobre PostgreSQL 15 y las 38 migraciones. |
| Backend focalizado | 43/43 de tarifas, checkout, cobro y SIFEN. |
| TypeScript móvil | Sin errores. |
| ESLint móvil | Sin errores ni warnings. |
| Jest móvil | 13 suites/24 pruebas aprobadas. |
| Expo Doctor | 20/20 comprobaciones aprobadas. |
| Export web móvil | 29 rutas estáticas, incluido tracking. |
| Frontend web | ESLint limpio, 5 archivos/10 pruebas y build Vite aprobados. |

El primer comando conjunto móvil agotó el tiempo sin emitir resultados; Jest se repitió aislado y terminó 13/13. Doctor y export también terminaron correctamente. Metro descartó una caché serializada incompatible y realizó un crawl completo; el export fue exitoso.

## Límites declarados

- Las tarifas iniciales son valores de arranque para desarrollo/comercio y deben revisarse desde administración antes de producción.
- La interfaz admite proveedores futuros, pero todavía no llama APIs externas de transportadoras.
- Zona está modelada para configuración futura; la libreta actual cotiza por ciudad/departamento y reglas generales.
- La pantalla de tracking ya funciona con un `pedidoId`; el historial y detalle navegable de todos los pedidos pertenecen a Fase 7.
- El backend en vivo no se reinició; la V38 se validó sobre PostgreSQL embebido limpio para preservar los procesos solicitados.

## Criterios de aceptación

- [x] Arquitectura `ShippingProvider` extensible.
- [x] Retiro, entrega local, otras ciudades/departamentos y transportadora configurables.
- [x] Reglas por ubicación, peso, subtotal, transportista, promoción y umbral gratis.
- [x] `Envio` y `EnvioEvento` multi-tenant con estados y tracking.
- [x] Mobile sin tarifas hardcodeadas.
- [x] Costo coherente con cobro y documentos fiscales.
- [x] Contrato, pruebas, lint, Doctor y builds en verde.

La Fase 7 puede consumir los pedidos y envíos ya expuestos para implementar historial, detalle y timeline por pedido.
