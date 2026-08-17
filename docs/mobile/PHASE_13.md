# Fase 13 — E2E, performance y accesibilidad

**Estado:** completada y validada  
**Fecha de cierre:** 2026-08-13  
**Alcance:** preparación reproducible de recorridos críticos, virtualización de listas y semántica para tecnologías asistivas.

## Resultado

La app dispone de flujos Maestro mantenibles para compra/tracking y pedido/chat, sin coordenadas ni credenciales versionadas. Un validador local analiza todos los YAML, comprueba subflujos, variables documentadas y que cada selector por ID corresponda a un `testID` real de React Native.

Los recorridos completos no se declaran ejecutados: necesitan APK/Development Build, dispositivo o emulador, backend accesible y fixtures controlados. La ejecución real se realizará con el artefacto de Fase 15; el escenario de respuesta desde Admin Web permanece manual por requerir un segundo actor.

## Flujos E2E preparados

- Flujo A: registro → logout/login → catálogo → producto → carrito → dirección → envío → pago → checkout → tracking.
- Flujo B: login → pedido → conversación asociada → mensaje → muerte del proceso → recuperación REST → reconexión WebSocket.
- Complemento manual: recepción de una respuesta real de Admin Web dentro de una conversación conocida.
- Credenciales y datos de fixtures se reciben exclusivamente por variables `E2E_*` del entorno.

La guía operativa completa está en `docs/mobile/E2E.md`.

## Performance

- Catálogo, pedidos, conversaciones y notificaciones migrados a `FlatList` virtualizada.
- Ventana acotada, render inicial y lotes limitados para reducir vistas montadas y trabajo del hilo JS.
- Paginación/infinite query existente preservada; ningún listado descarga todo el backend de una vez.
- Tarjetas mantienen claves estables por ID.
- Imágenes de producto usan Expo Image con `cachePolicy="memory-disk"` y `recyclingKey` para evitar descargas repetidas y contenido anterior durante el reciclado.
- No se agregaron librerías de listas pesadas: `FlatList` cubre el volumen y diseño actual.

Referencias consultadas: [performance en React Native](https://reactnative.dev/docs/performance.html), [FlatList](https://reactnative.dev/docs/flatlist) y [Expo Image SDK 57](https://docs.expo.dev/versions/v57.0.0/sdk/image/).

## Accesibilidad

- Los títulos principales exponen rol `header`.
- Inputs conectan etiqueta visual, `accessibilityLabelledBy`, hint de ayuda/error, estado disabled y errores como live region.
- Botones conservan rol, busy/disabled y área táctil mínima centralizada.
- Precios y badges de no leídos se agrupan como elementos accesibles con texto completo.
- Pedidos, conversaciones y notificaciones agregan hints que explican la navegación o el estado no leído.
- El estado “En vivo/Reconectando” del chat usa una región viva educada.
- Imágenes usan `alt`; los estados no dependen solo del color porque también incluyen texto.
- La navegación E2E por accessibility tree actúa como control adicional de que los elementos clave sean descubribles.

La prueba final con TalkBack/VoiceOver y escalado de fuente requiere dispositivos físicos y forma parte del checklist de Fase 15.

## Evidencia de validación

| Control | Resultado |
| --- | --- |
| Backend unitario | 315/315 pruebas aprobadas. |
| Migraciones PostgreSQL 15 | `MigracionesIT` 15/15; 42 migraciones. |
| Frontend web | ESLint, 6 archivos/12 pruebas y build aprobados. |
| TypeScript móvil | Sin errores. |
| ESLint móvil | Sin errores ni warnings de React Compiler. |
| Jest móvil | 21 suites/52 pruebas; incluye asociación accesible de input/error. |
| Maestro estático | 5 YAML, 31 selectores y 12 variables validados. |
| Expo Doctor | 20/20 comprobaciones. |
| Export Android | Bundle Hermes Android generado correctamente, 5,4 MB. |
| Higiene | `git diff --check`, secretos móviles y puertos reservados sin errores. |

## Archivos principales de esta fase

- `techstore-mobile/e2e/maestro/config.yaml`
- `techstore-mobile/e2e/maestro/common/login.yaml`
- `techstore-mobile/e2e/maestro/flows/flow-a-purchase.yaml`
- `techstore-mobile/e2e/maestro/flows/flow-b-chat-recovery.yaml`
- `techstore-mobile/e2e/maestro/manual/flow-b-admin-reply.yaml`
- `techstore-mobile/scripts/validate-e2e.cjs`
- `techstore-mobile/src/features/search/screens/SearchScreen.tsx`
- `techstore-mobile/src/features/orders/screens/OrdersScreen.tsx`
- `techstore-mobile/src/features/messaging/screens/ConversationsScreen.tsx`
- `techstore-mobile/src/features/notifications/screens/NotificationsScreen.tsx`
- `techstore-mobile/src/components/ui/{Input,Display}.tsx`
- `techstore-mobile/src/components/common/Screen.tsx`
- `techstore-mobile/tests/components.test.tsx`
- `techstore-mobile/package.json`
- `docs/mobile/E2E.md`

## Nueva migración

Ninguna. La Fase 13 no modifica el modelo de datos.

## Pendientes

- Ejecutar Maestro sobre el APK final y fixtures descartables de Fase 15.
- Validar la respuesta Admin Web coordinada, TalkBack, VoiceOver, escalado grande y scroll en el Xiaomi Redmi Note 8 Pro.
- Medir inicio, FPS, memoria y red en build release físico; el export local solo valida el bundle.
- Maestro CLI no está instalado en esta estación; no se instaló una herramienta global innecesaria antes de disponer del APK final.

## Criterios de aceptación

- [x] Flujos prioritarios A y B declarados con datos externos y selectores estables.
- [x] Validación automática de sintaxis, subflujos, variables y IDs.
- [x] Listas comerciales principales virtualizadas y paginadas.
- [x] Cache/recycling de imágenes activado.
- [x] Formularios, estados dinámicos y controles clave mejorados para lectores de pantalla.
- [x] Regresión backend, web, móvil, Doctor, export y migraciones en verde.
