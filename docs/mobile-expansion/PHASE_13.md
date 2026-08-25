# FASE 13 — RENDIMIENTO

ESTADO: COMPLETED

## Mediciones

| Artefacto | Resultado | Interpretación |
|---|---:|---|
| APK debug universal | 229,57 MiB | Incluye JIT/símbolos y tres ABI; no representa distribución. |
| APK production release arm64 | 22,4 MB comprimido | Artefacto AOT medido con `--analyze-size`. |
| AOT propio TechStore | 345 KB descomprimido | La mayor parte del binario es runtime Flutter/plugins nativos. |
| Chunk Web inicial | 234,66 KB / 75,68 KB gzip | Antes superaba 500 KB; ya no existe warning de chunk grande. |
| CSS Web | 24,83 KB / 5,58 KB gzip | Sin regresión relevante. |

Tree-shaking redujo Cupertino Icons 99,7% y Material Icons 99,3%.

## Correcciones

- Web aplica `React.lazy` por ruta y `Suspense`; cada pantalla administrativa se carga bajo demanda.
- El catálogo Mobile limita decodificación de imágenes al ancho físico visible (`cacheWidth`, máximo 2048), evitando retener originales sobredimensionados.
- Flutter mantiene `ListView.builder`, `GridView.builder` y slivers en listados de negocio.
- Providers de consultas son `autoDispose`; búsquedas de catálogo se envían al confirmar, no por cada tecla.
- Caché de catálogo reduce requests ante cortes y realtime invalida/revalida sin polling.
- Endpoints listados usan paginación y la base conserva índices auditados en Fases 00/03.
- Compresión de uploads ocurre antes de red; timeouts y reintentos están acotados.

## Pruebas

- [x] Build Web production con code splitting, sin warning de tamaño.
- [x] Web lint sin warnings y 14/14 tests tras liberar recursos.
- [x] APK release arm64 analizado: 22,4 MB.
- [x] Flutter analyze sin incidencias tras optimización de imágenes.
- [x] Tests dirigidos de UI/repositorios: 5/5.
- [x] Stack Web/backend continúa operativo.

## Observación

Tiempo de arranque, memoria, batería y jank en hardware real requieren perfil físico y se vuelven a comprobar en Fase 16. No se aplicaron microoptimizaciones sin evidencia.
