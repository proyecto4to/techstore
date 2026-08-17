# Fase 3 — Home, catálogo y favoritos

**Estado:** completada  
**Fecha de cierre:** 2026-08-10  
**Alcance:** Home, catálogo, búsqueda, filtros, detalle de producto y favoritos.

## Resultado

TechStore Mobile consume el catálogo real mediante TanStack Query. Home muestra productos y marcas, Buscar ofrece filtros y paginación incremental, el detalle valida visibilidad y los usuarios autenticados pueden guardar o quitar favoritos.

## Backend y seguridad

- `ProductoPublicResponse` reemplaza el DTO administrativo en los GET públicos.
- Costo, margen, stock real/comprometido/disponible exacto, mínimos, reposición, flags y timestamps internos no se serializan a la tienda.
- `/productos/admin` y `/productos/admin/{id}` conservan la información operativa bajo rol Admin; el frontend web administrativo fue migrado a esas rutas.
- Listado y detalle públicos comparten reglas de activo y visibilidad.
- La búsqueda cubre nombre, descripción, marca y modelo; filtros por marca/disponibilidad y orden usan paginación.
- V36 crea favoritos únicos por tenant+usuario+producto, índices, RLS forzado y políticas fail-closed/mantenimiento.
- Los endpoints de favoritos derivan usuario y tenant de la autenticación, nunca del cuerpo o una cabecera libre.

## Aplicación móvil

- Home remoto con saludo, búsqueda, accesos por marca, recomendados y sección de favoritos.
- Catálogo con debounce, filtros por marca/disponibilidad, orden por nombre/precio/novedad y carga incremental.
- Detalle con foto, marca/modelo, precio, IVA, disponibilidad comercial y favorito.
- Tres fotografías realistas locales actúan como fallback para rutas de imagen no portables; `/uploads/` y URLs HTTP(S) válidas se resuelven de forma remota.
- Favoritos requieren sesión; si el visitante toca el corazón, se lo dirige al login.
- Zod estricto valida catálogo/favoritos y rechaza respuestas que vuelvan a filtrar campos internos.

## Compatibilidad web

La tienda web usa la nueva señal booleana `disponible`; deja de mostrar cantidades internas y recuerda que stock/precio se revalidan al checkout. Venta directa, compras y productos Admin usan el endpoint interno y conservan las cantidades exactas que necesitan.

## Evidencia de validación

| Control | Resultado |
| --- | --- |
| Backend unitario | 276 pruebas, 0 fallos/errores. |
| Backend integración | 33 pruebas, V1–V36 en PostgreSQL 15 real, 0 fallos/errores. |
| Contrato HTTP nuevo | Catálogo sin campos internos y ciclo favorito add/list/delete aprobados. |
| Frontend web | ESLint limpio, 5 archivos/10 tests aprobados con un worker y Vite build correcto. |
| `npm run check` móvil | 6 suites/12 tests, TypeScript y lint limpios. |
| `npx --no-install expo-doctor` | 20/20 comprobaciones aprobadas. |
| `npm run build:web` móvil | Export estático correcto, 19 rutas. |
| QA visual interactivo | No disponible: no había navegador integrado conectado; no se sustituyó por otra herramienta. |

La primera ejecución paralela de Vitest web agotó el tiempo de arranque de sus workers sin cargar tests. La repetición serial aprobó los 10 casos; lint y build también pasaron. El `verify` con empaquetado no pudo renombrar el JAR porque el backend visible estaba abierto: se conservaron esos procesos y se ejecutaron unitarias e integraciones sin reempaquetar.

## Imágenes generadas

Se conservan tres PNG cuadrados: notebook Titanium, monitor profesional de 27 pulgadas y teclado mecánico compacto. El prompt set común fue fotografía de producto premium realista en estudio, fondo continuo gris cálido, materiales grafito, acentos azul marino/dorado, sin marcas, logos, texto ni marcas de agua.

## Criterios de aceptación

- [x] Home consume datos reales con estados de carga/error y fallback explícito.
- [x] Catálogo paginado, búsqueda y filtros funcionales.
- [x] Detalle no devuelve productos ocultos/inactivos.
- [x] DTO público sin información interna.
- [x] Favoritos persistidos por usuario y tenant con RLS.
- [x] Frontend web compatible con la separación público/Admin.
- [x] Contrato OpenAPI y tipos generados actualizados.
- [x] Backend, web y móvil en verde.

La siguiente fase puede construir el carrito móvil sobre IDs/cantidades y revalidación backend, sin persistir snapshots de precio o stock como autoridad.
