# Fase 1 — Fundación móvil y Titanium Glass

**Estado:** completada  
**Fecha de cierre:** 2026-08-10  
**Alcance:** base Expo universal, navegación, sistema visual, componentes, shells de pantallas y calidad automatizada.

## Resultado

TechStore cuenta con una aplicación Expo SDK 57 universal, estructurada para Android, iOS y web y preparada para crecer por features. La interfaz inicial permite observar el producto y validar la dirección visual, pero usa contenido de muestra y no ejecuta operaciones comerciales. En este cierre se validó el export web; la compilación nativa debe verificarse en un development build por plataforma.

## Entregado

- Proyecto `techstore-mobile` con Expo Router, TypeScript estricto y alias `@/`.
- Configuración de marca, icono, splash, esquema `techstore://`, IDs nativos y export web estático.
- Perfiles EAS `development`, `preview` y `production`.
- Stack raíz y navegación inferior con `Inicio`, `Buscar`, `Carrito`, `Pedidos` y `Cuenta`.
- Rutas reservadas para autenticación y detalle de producto.
- Tema Titanium Glass oscuro/claro, integración con el tema del sistema y tokens semánticos.
- Providers globales para safe area, gestos, tema/navegación y TanStack Query.
- Biblioteca base de texto, botones, inputs, cards, badges, productos, feedback, modal, estados de pedido, timeline y chat bubble.
- Home de demostración, shells de búsqueda/carrito/cuenta/login y timeline de pedido explícitamente simulado.
- Jest/Testing Library, ESLint estricto, typecheck y export web automatizado.
- Documentación de arquitectura, setup y desarrollo local.

## Límites deliberados

No forman parte de esta fase:

- Login, refresh, logout, revocación, biometría o sesión persistente reales.
- Cliente HTTP, contratos OpenAPI, interceptores o selección funcional de tenant.
- Catálogo, búsqueda y detalle provenientes del backend.
- Carrito persistente, validación de precio/stock, checkout o pagos.
- Pedidos reales, tracking, WebSocket/STOMP, chat y notificaciones push.
- Modo offline, sincronización, publicación OTA o entrega en tiendas.

Las dependencias que habilitarán esas capacidades pueden estar instaladas/configuradas, pero no deben presentarse como funcionalidad terminada.

## Evidencia de validación

Al cierre se ejecutaron correctamente:

| Control | Resultado |
| --- | --- |
| `npm run typecheck` | Sin errores de TypeScript. |
| `npm run lint` | Sin errores ni warnings. |
| `npm test` | 2 suites, 4 pruebas aprobadas. |
| `npm run check` | Cadena typecheck + lint + tests aprobada. |
| `npm run doctor` | 20/20 comprobaciones aprobadas. |
| `npx expo install --check` | Dependencias Expo compatibles. |
| `npm run build:web` | Export estático correcto, 19 rutas generadas. |
| QA visual web a `390 × 844` | Inicio, encabezado, contenido y cinco tabs visibles; ruta raíz sin colisiones. |

El export web demuestra que las rutas compilan; no reemplaza una compilación/instalación Android. El development build sigue siendo el entorno recomendado para aceptación nativa.

## Auditoría de dependencias

`npm audit` informó 23 nodos afectados: `15 high`, `8 moderate` y **`0 critical`**. El análisis redujo el resultado a advisories transitivos del tooling:

- `image-size@1.2.1`, incorporado por Expo/Metro para procesar assets durante build, con avisos de denegación de servicio sobre formatos específicos.
- `uuid@7.0.3`, incorporado por el paquete de tooling iOS `xcode`; la ruta observada usa UUID v4 y no se demostró explotación en el runtime móvil.

No existe un parche directo compatible dentro del árbol actual de Expo SDK 57. Esto no autoriza a ignorar los avisos: se aceptan temporalmente como riesgo de tooling, se restringen los assets de build a fuentes confiables, se mantienen timeouts de CI y se monitorean actualizaciones de Expo/Metro.

**No ejecutar `npm audit fix --force`.** La propuesta automática fuerza cambios incompatibles (incluidos downgrades de Expo/React Native) y puede romper el proyecto. Cada futura actualización debe pasar `npx expo install --check`, `npm run doctor` y `npm run check`.

## Criterios de aceptación de la fase

- [x] Raíz móvil aislada dentro del repositorio TechStore.
- [x] Configuración Expo SDK 57 coherente.
- [x] Navegación principal visible y estable.
- [x] Sistema de diseño centralizado, claro/oscuro y usable en web/móvil.
- [x] Componentes fundamentales reutilizables.
- [x] Contenido simulado identificado como tal.
- [x] Controles automatizados en verde.
- [x] Instrucciones para ver la aplicación en Android y web.
- [x] Riesgos npm registrados sin aplicar correcciones incompatibles.

## Punto de partida de la Fase 2

La siguiente fase debe diseñar e implementar la integración segura: contratos de API, configuración por ambiente/tenant, autenticación y ciclo de sesión, almacenamiento de tokens en SecureStore, manejo uniforme de errores y pruebas. Hasta completar esos controles, el botón de login debe permanecer deshabilitado y no se deben almacenar credenciales reales.
