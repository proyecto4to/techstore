# Matriz de paridad visual TechStore

**Actualizada:** 2026-08-11  
**Objetivo:** identidad compartida con el frontend web y comportamiento móvil nativo.

| Web | Mobile | Estado | Criterio aplicado |
| --- | --- | --- | --- |
| Fondo global | `TechStoreBackground` dentro de `Screen` | Completo | Gradiente oscuro y halos azul/violeta. |
| `isotipo.svg` | `TechStoreBrand` | Completo | Asset local y geometría SVG oficial. |
| `.card` | `Card` | Completo | Fondo `#1E293B`, borde `#475569`. |
| `.card-hover` | `PressableCard` / Pressable | Completo | Opacidad, escala y borde al presionar. |
| `.btn-primary` | `Button` primary | Completo | Gradiente `#0F66E6 → #7C3AED`. |
| `.btn-secondary` | `Button` secondary | Completo | Transparente, borde oscuro, texto claro. |
| `.btn-danger` | `Button` danger | Completo | Rojo semántico y estados accesibles. |
| `.input` | Inputs y búsqueda | Completo | Fondo `#334155`, borde `#475569`, focus azul. |
| `.gradient-text` | `TechStoreBrand` | Completo con fallback | Marca en azul oficial; SVG conserva gradiente nativo. |
| Navbar | Header móvil + tabs | Completo | Branding arriba y cinco destinos abajo. |
| Navegación activa | Expo Router tabs | Completo | Activo `#3E82F0`, inactivo `#94A3B8`. |
| Badge carrito | Tab badge | Completo | Cian `#06B6D4`, cantidad derivada del store. |
| `ProductCard` | `ProductCard` | Completo | Imagen, chip, precio azul, favorito, stock y CTA. |
| Formato precio | `Price` | Completo | `Intl.NumberFormat('es-PY')`, moneda backend, PYG sin decimales. |
| Badges de estado | `Badge` / `OrderStatusBadge` | Completo | Fondos translúcidos y etiquetas explícitas. |
| Alerts | Toast, estados y banners | Completo | Color, borde, icono/texto y mensajes humanos. |
| Skeleton | `Skeleton` | Completo | Tonos oscuros oficiales. |
| Login/Registro | Screens Auth | Completo | Fondo, branding, card, inputs y CTA oficiales. |
| Catálogo/Detalle | Screens existentes | Completo | Componentes compartidos; lógica intacta. |
| Carrito | `CartScreen` | Completo | Cards, selector, resumen, CTA y badge. |
| Showcase | `/dev/design-system` | Completo | Visible bajo `__DEV__`; redirección en producción. |

La regresión automatizada quedó completa. La inspección visual interactiva no pudo ejecutarse porque no había navegador integrado conectado; debe repetirse a 390 × 844 antes de aceptación visual humana.
