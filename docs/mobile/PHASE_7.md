# Fase 7 — Mis pedidos, detalle y timeline

**Estado:** completada  
**Fecha de cierre:** 2026-08-11  
**Alcance:** historial paginado de pedidos propios, detalle comercial y línea temporal logística.

## Resultado

La pestaña Pedidos dejó de mostrar datos de diseño. El usuario autenticado ve su historial real, ordenado del más reciente al más antiguo, abre cada compra y consulta productos, total, envío, dirección, pago y eventos. Los estados comerciales y logísticos se muestran por separado para no afirmar que un pedido pagado ya fue despachado. Un pedido todavía pendiente puede cancelarse mediante confirmación explícita.

## Entregado

- Historial autenticado sobre `GET /api/v1/pedidos`, paginado de 10 en 10 y con carga incremental.
- Pull-to-refresh, skeletons, estado vacío, error y reintento.
- Tarjetas con número, fecha/hora `es-PY`, estado comercial y total canónico.
- Ruta tipada `/orders/[pedidoId]` y detalle validado con Zod estricto.
- Productos con cantidad, unitario, subtotal e IVA informado por backend.
- Desglose de subtotal de productos, costo de envío y total.
- Dirección histórica del pedido, método de entrega, intención de pago y notas.
- Timeline del envío cuando existe; fallback honesto para pedidos históricos sin `Envio`.
- Acceso al seguimiento completo desde el detalle.
- Cancelación exclusiva de pedidos pendientes con modal de confirmación, liberación de stock y cancelación logística ya provistas por backend.
- Invalidación de cachés de historial, detalle y envío después de cancelar.
- Mapeo explícito de estados `P/C/G/N/R/X` a etiquetas móviles, incluido Devuelto.
- OpenAPI móvil 1.5 y tipos regenerados.

## Seguridad

- La app nunca envía usuario o tenant para listar/abrir/cancelar: el backend usa `Authentication` y ownership.
- Conocer un `pedidoId` no autoriza el acceso; el endpoint rechaza pedidos ajenos.
- Los DTO del historial no aceptan campos internos de tenant o inventario; existe una prueba Zod de regresión.
- La cancelación no es optimista: la UI cambia solo después de la confirmación del servidor.

## Evidencia de validación

| Control | Resultado |
| --- | --- |
| Backend unitario vigente | 293/293 pruebas aprobadas; Fase 7 reutiliza endpoints backend ya cubiertos. |
| TypeScript móvil | Sin errores. |
| ESLint móvil | Sin errores ni warnings. |
| Jest móvil | 15 suites/29 pruebas aprobadas. |
| Expo Doctor | 20/20 comprobaciones aprobadas. |
| Export web móvil | 30 rutas estáticas, incluido `/orders/[pedidoId]`. |

## Límites declarados

- Los pedidos anteriores a V38 pueden no tener una entidad `Envio`; se muestra su evento comercial inicial sin inventar tracking.
- Descarga móvil autenticada de factura PDF no se añadió: requiere diseñar transferencia/almacenamiento nativo seguro en una fase posterior.
- Contactar a TechStore desde un pedido se habilita con conversaciones asociadas en Fase 8.
- La UI no simula un estado de pago o transporte que el backend no haya confirmado.

## Criterios de aceptación

- [x] Mis pedidos consume historial real y paginado.
- [x] Detalle muestra información comercial completa.
- [x] Timeline consume `EnvioEvento` cuando existe.
- [x] Estados comerciales y logísticos no se mezclan.
- [x] Cancelación destructiva exige confirmación y respuesta de servidor.
- [x] Contrato, pruebas, lint, Doctor y export en verde.

La Fase 8 puede enlazar una conversación a `pedidoId` desde este detalle sin modificar su autorización.
