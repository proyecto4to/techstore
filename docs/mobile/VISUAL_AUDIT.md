# Auditoría visual móvil — Fases 1 a 4

**Fecha:** 2026-08-11  
**Fuente de verdad:** `techstore-frontend/tailwind.config.js`, `src/App.css`, los SVG oficiales y los componentes web reales.

## Hallazgo general

La aplicación móvil conserva una identidad propia anterior —azul petróleo, dorado, superficies glass y tema claro opcional— que no coincide con TechStore web. La lógica funcional de autenticación, catálogo, favoritos y carrito está separada suficientemente de la presentación y puede preservarse. El refactor debe concentrarse en tokens, contenedores, iconografía y componentes visuales compartidos.

## Diferencias del sistema visual

| Área | Móvil actual | Web oficial | Corrección requerida |
| --- | --- | --- | --- |
| Tema | Oscuro, claro y preferencia del sistema | Oscuro único | Fijar tema oscuro y retirar selector visible. |
| Fondo | Plano `navy950` | Gradiente `#0F172A → #1A1F3A` con halos azul/violeta | Crear `TechStoreBackground` liviano y usarlo en `Screen`. |
| Primario | Dorado | Azul `#0F66E6`, `#3E82F0` | Sustituir tokens semánticos. |
| Secundario | Azul petróleo | Violeta `#7C3AED` | Incorporar token secundario y gradientes. |
| Acento | Dorado | Cian `#06B6D4` | Usar para badges y detalles. |
| Cards | Variantes glass translúcidas | `#1E293B`, borde `#475569` | Conservar API de `Card`, unificar apariencia oficial. |
| Botón principal | Fondo sólido dorado | Gradiente azul-violeta | Implementar gradiente sin cambiar handlers. |
| Inputs | Superficie Titanium | `#334155`, borde `#475569`, focus azul | Migrar componente base. |
| Iconos | Ionicons | Lucide | Migrar componentes base y tabs a `lucide-react-native`. |
| Branding | Texto/símbolos genéricos | Isotipo y logos SVG oficiales | Reutilizar el isotipo oficial en un componente `Brand`. |
| Tipografía | Pesos 800 y escala genérica | Inter 400/500/600/700 | Definir escala semántica móvil y evitar peso 800. |

## Auditoría por pantalla

| Pantalla | Diseño actual y componentes reutilizables | Diferencia principal | Corrección |
| --- | --- | --- | --- |
| Home | `Screen`, búsqueda, chips, `ProductCard`, queries y fallbacks reales | Hero/card glass y acentos dorados; falta branding oficial | Mantener queries/navegación; aplicar fondo, encabezado de marca, promo azul-violeta y cards web adaptadas. |
| Buscar/Catálogo | `Screen`, `Input`, chips, grilla/lista y paginación | Input, filtros y cards usan Titanium | Migrar únicamente componentes y colores; conservar debounce, filtros y carga incremental. |
| Detalle | Query, favorito, cantidad y alta al carrito ya funcionales | Cards glass, precio/acciones dorados, sin feedback “Agregado” | Aplicar superficie oficial, precio azul, badges semánticos y feedback temporal sin alterar store. |
| Carrito | Persistencia mínima, validación backend, cantidades, errores y totales completos | Cards glass y CTA sólido; badge ausente en tabs | Aplicar cards oficiales, CTA gradiente y badge cian derivado del store. |
| Login | React Hook Form, Zod y autenticación real | Card glass y branding textual | Fondo TechStore, isotipo oficial, input oscuro y CTA gradiente. |
| Registro | Formulario y mutation reales | Misma divergencia que Login | Mismo shell visual de Auth sin tocar validaciones. |
| Recuperar contraseña | Placeholder funcionalmente honesto | Presentación Titanium | Aplicar shell oficial y mantener límite declarado. |
| Cuenta | Perfil/logout reales y selector de tema | El selector contradice dark-only | Retirar controles de apariencia y presentar identidad/acciones con cards oficiales. |
| Pedidos | Placeholder explícito | Card glass y lenguaje visual anterior | Revestir con estados oficiales; conservar placeholder hasta Fase 7. |
| Navegación inferior | Cinco tabs correctos | Ionicons, colores y badge del carrito sin implementar | Lucide, activo azul, inactivo muted, superficie oscura y badge cian. |

## Componentes reutilizables

- Conservar las APIs de `Screen`, `AppText`, `Card`, `Button`, `Input`, `Badge`, `Chip`, `Price`, `ProductCard`, `ProductImage`, `QuantitySelector`, estados y feedback.
- Conservar todos los hooks, services, stores, schemas, queries, mutations y rutas.
- Convertir `Screen` en el punto único de fondo/encabezado para evitar estilos repetidos.
- Mantener temporalmente nombres de variante como `glass` por compatibilidad, pero renderizarlos con la superficie oficial hasta limpiar usos sin riesgo.

## Estilos obsoletos detectados

- Paleta `navy/gold/paper` y color scheme claro.
- Preferencia `system/light/dark` y texto “Titanium Glass”.
- `surfaceGlass` como identidad primaria.
- Ionicons en UI base y tabs.
- Pesos tipográficos 800.
- Fondos planos en todas las pantallas.

## Riesgos de refactor

- Cambiar nombres de iconos rompe tipos/consumidores; conviene mapear una API interna estable hacia Lucide.
- El SVG oficial debe empaquetarse como asset local y no depender del servidor web.
- El gradiente y halos deben permanecer detrás del scroll y no capturar eventos táctiles.
- El badge del carrito debe usar una selección pequeña del store para evitar rerenders globales.
- La pantalla showcase debe excluirse en producción mediante guard de ruta, no solo ocultarse de la navegación.

