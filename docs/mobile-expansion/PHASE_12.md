# FASE 12 — UI/UX

ESTADO: COMPLETED

## Resultado

- Experiencia Flutter nativa con Material 3; no se usa WebView.
- Temas claro/oscuro derivados de un mismo `ColorScheme`.
- Navegación por módulos y permisos, deep links internos y retorno nativo.
- Home adaptativo mediante `SliverGridDelegateWithMaxCrossAxisExtent`, tarjetas de altura segura y texto de hasta dos líneas.
- Catálogo responsive por extensión máxima, búsqueda, filtros, pull-to-refresh y feedback de carrito.
- Estados reutilizables `AppLoading`, `AppEmptyState` y `AppErrorState` con padding, live regions, semántica y retry.
- Catálogo y detalle migrados a estados consistentes; imágenes anuncian el producto a lectores de pantalla.
- Tooltips completados en búsqueda, cantidades, líneas de venta/compra y acciones de iconos.
- Indicadores globales de conectividad, operaciones pendientes, biometría y actualización obligatoria.
- Formularios usan teclado/tipo de campo, validación, busy states y feedback por snackbar/dialog.

## Accesibilidad y responsive

- Controles Material conservan objetivos táctiles de 48 dp.
- Iconos decorativos quedan excluidos cuando la tarjeta ya posee etiqueta semántica.
- Estados vacíos/error toleran ancho 320 y escalado de texto 2× sin overflow.
- Grillas principales usan máximo por elemento en lugar de asumir un número fijo de columnas.
- `SafeArea`, scroll y límites de ancho cubren pantallas compactas, tablets y teclado en formularios críticos.

## Pruebas

- [x] Flutter analyze sin incidencias.
- [x] Suite Flutter completa: 19/19.
- [x] Widget tests de semántica, retry y texto 2×.
- [x] APK Development debug recompilado.
- [x] Auditoría estática de tooltips y estados async.

## Archivos principales

- `techstore-mobile/lib/core/widgets/async_states.dart`
- `techstore-mobile/lib/app/home_page.dart`
- `techstore-mobile/lib/features/catalog/presentation/catalog_page.dart`
- `techstore-mobile/test/core/async_states_test.dart`
