# Fase 4 — Carrito, persistencia mínima y revalidación

**Estado:** completada  
**Fecha de cierre:** 2026-08-11  
**Alcance:** carrito móvil persistente, revalidación de precio y disponibilidad contra el backend y estados de interfaz asociados.

## Resultado

TechStore Mobile mantiene un carrito local versionado compuesto únicamente por identificadores de producto y cantidades. Antes de presentar totales o permitir continuar, la aplicación consulta al backend, que vuelve a cargar el catálogo vigente, verifica visibilidad y stock y recalcula subtotales y totales. El cliente no trata sus snapshots locales como autoridad comercial y esta fase no reserva stock ni crea pedidos.

## Entregado

- Store Zustand persistido en AsyncStorage bajo una clave versionada y separada por tenant configurado.
- Persistencia mínima de `{ productoId, cantidad }`; no se guardan precios, stock ni DTOs completos.
- Saneamiento al restaurar datos, fusión de líneas duplicadas y límite de 99 unidades por producto.
- Acciones para agregar, cambiar cantidad, quitar y vaciar el carrito.
- Badge del carrito en la navegación y alta desde el detalle de producto.
- `POST /api/v1/carrito/validar` público, con request acotado y validado.
- Revalidación backend en una sola consulta por lote, sin reservar stock.
- Estados explícitos para producto disponible, no encontrado, no disponible, cantidad insuficiente y cantidad excesiva.
- DTO público del producto en las líneas válidas, sin exponer costo, margen ni cantidades internas de inventario.
- Totales agrupados por moneda; no se suman monedas incompatibles ni se calculan totales cuando alguna línea es inválida.
- Pantalla móvil con hidratación, carga, error/reintento, carrito vacío, corrección de cantidades y resumen calculado por el servidor.
- Contrato OpenAPI y tipos móviles generados actualizados.

## Seguridad e integridad comercial

- El cliente nunca envía precio, moneda, subtotal, stock ni tenant como autoridad.
- El backend deriva el catálogo desde el contexto seguro y recalcula todos los importes.
- La validación no compromete inventario; el checkout deberá volver a verificar y reservar dentro de su propia transacción.
- La persistencia local se considera preferencia no sensible y se valida antes de utilizarse.

## Evidencia de validación

| Control | Resultado |
| --- | --- |
| Backend unitario | 280 pruebas, 0 fallos/errores. |
| `CatalogoPublicoIT` | 2 pruebas HTTP aprobadas sobre PostgreSQL 15 real, incluidas 36 migraciones y `/api/v1/carrito/validar`. |
| `npm run check` móvil | TypeScript y lint limpios; 9 suites/17 pruebas aprobadas. |
| `npm run doctor` | 20/20 comprobaciones aprobadas. |
| `npm run build:web` | Export estático correcto, 19 rutas generadas. |

El `verify` completo alcanzó 280/280 pruebas unitarias, pero el plugin de Spring Boot no pudo renombrar el JAR porque el backend visible lo mantiene abierto. No se detuvo ese proceso. Para completar la evidencia pendiente se ejecutó Failsafe directamente y `CatalogoPublicoIT` aprobó 2/2.

## Límites declarados

- No hay checkout, dirección, envío, pago, idempotencia ni creación de pedido en esta fase.
- El carrito no reserva stock y su validación puede quedar obsoleta; el checkout debe revalidar transaccionalmente.
- La separación de la clave local depende del tenant configurado; no se confía en esa clave para autorización backend.
- La interfaz conserva todavía el lenguaje visual anterior y será refactorizada de forma controlada en la Fase 4.5.

## Criterios de aceptación

- [x] Persistencia mínima y versionada.
- [x] Datos restaurados saneados.
- [x] Precio y stock recalculados exclusivamente por el backend.
- [x] Estados inválidos representados sin inventar éxito.
- [x] Totales seguros por moneda.
- [x] Carrito integrado con catálogo, detalle y navegación.
- [x] Pruebas backend, integración HTTP y controles móviles en verde.
- [x] Build web y Expo Doctor aprobados.

La Fase 4.5 puede cambiar únicamente la presentación del carrito y del resto de pantallas existentes. La Fase 5 podrá reutilizar estas líneas mínimas para implementar dirección, checkout e idempotencia sin confiar en el estado local como fuente de verdad.
