# Fase 9 — Rendimiento y seguridad

Fecha: 2026-10-19. Rama: `rediseno-monokai`.

## Cómo se midió

Flutter dibuja en su propia superficie, así que `gfxinfo` no sirve. Se leyó el tiempo de cada cuadro de esa superficie con `dumpsys SurfaceFlinger --latency` mientras se deslizaba el catálogo de forma automática: cinco deslizadas de 600 px, tres pasadas seguidas, siempre igual. «Lento» es un cuadro que tardó más de 1,5 veces el período de la pantalla (25 ms a 60 Hz).

Emulador: Android 15, 540×1200 a 210 dpi, GPU del equipo por gfxstream, aceleración WHPX, 4 núcleos y 2 GB. Como referencia, la app de Ajustes del sistema da 54 fps con 3,2 % de cuadros lentos: con Impeller hay un piso de unos 15 ms de dibujo por cuadro que no depende de la app, así que unos 55–58 fps es el techo práctico de este emulador.

## Resultados

| Versión | Pasada 1 (recién instalada) | Pasada 3 |
|---|---|---|
| Inicio del rediseño: el análisis de cada foto leía los píxeles desde la GPU | 12,1 fps, 86 % lentos | 45,9 fps, 22,6 % lentos |
| Análisis en Dart, en un isolate | 12,6 fps, 93 % lentos | 49,9 fps, 14,5 % lentos |
| Decodificador nativo de Android | 13,2 fps, 75 % lentos | 54,4 fps, 5,6 % lentos |
| **Caché de fotos corregida (final)** | **48,4 fps, 15 % lentos** | **54,5 fps, 10,5 % lentos** |
| Final, con las fotos ya en disco | 54,9 fps, 8,1 % lentos | **58,7 fps, 2,4 % lentos** |

## Qué lo trababa y qué se hizo

1. **Las fotos se volvían a bajar enteras cada vez que se mostraban.** El backend las manda con `Cache-Control: no-cache, no-store` —lo agrega Spring Security a toda respuesta— y `flutter_cache_manager` lo obedece: vigencia cero. Cada tarjeta que entraba en pantalla volvía a bajar su foto, y el catálogo pesa 10,3 MB en 29 fotos. Era la causa principal de la traba y además gastaba los datos de cada cliente en cada visita. Se corrigió en las dos puntas: la app da por buena cada foto una semana (`ServicioDeFotos`) y el backend manda una cabecera propia, un mes para las subidas (llevan un nombre único) y una semana para las empaquetadas. Con el backend corregido, también la tienda web deja de bajarlas en cada visita.
2. **Leer los píxeles de una foto ya subida a la GPU frenaba el dibujo** (hasta 330 ms por foto). El análisis que decide cómo presentar cada foto ahora usa una miniatura de 48 píxeles que arma el decodificador de Android en un hilo propio, con muestreo, sin armar nunca la foto entera en memoria. El resultado se guarda en la base local: cada foto se analiza una vez por instalación.
3. **Efectos caros en una lista.** Los fondos con patrón y marca de agua se rasterizan una vez y se reutilizan; el desenfoque de las fotos de escena es una imagen de 24 píxeles agrandada, no un filtro; los fundidos son degradados superpuestos, no máscaras; y solo entra animado lo que se ve al abrir.
4. **Precarga en los ratos libres**: mientras la persona mira la primera pantalla, se bajan y analizan las fotos que vienen, con prioridad ociosa.

## Mejoras recomendadas que no se hicieron

- **Comprimir las fotos al subirlas.** Las cuatro fotos subidas por administración son PNG de 1,1 a 1,6 MB para mostrarse a unos 400 píxeles. Achicarlas y convertirlas al subir —en el backend— bajaría el catálogo a una fracción. Cambia el procesamiento de subidas del backend en producción, así que queda para decidir.
- **Validar en un teléfono de 120 Hz** la frecuencia alta que ahora pide la app (fase 8).

## Revisión de seguridad

| Punto | Estado |
|---|---|
| Tráfico sin cifrar | Prohibido en producción (`usesCleartextTraffic=false`). Solo lo permiten el sabor de desarrollo (hacia el emulador) y el APK privado, que exige una IP de red local |
| Credenciales en el teléfono | Tokens en `flutter_secure_storage` (Keystore de Android); la app puede pedir biometría para operaciones sensibles |
| Registro de red | Solo método, ruta y código de estado; nunca cabeceras, cuerpo ni parámetros |
| Copia de seguridad de Android | Desactivada (`allowBackup=false`): los datos de la app no salen del teléfono |
| Componentes expuestos | Solo la actividad principal (lanzador y enlaces `techstore://`). El `FileProvider` comparte únicamente la carpeta del APK descargado |
| Actualización desde la app | El APK descargado se comprueba con SHA-256 antes de entregarlo al instalador del sistema, que pide confirmación |
| Permisos | Cada uno justificado en el manifiesto; los de audio y video que traían los plugins se quitan |
| Canal nativo nuevo (miniaturas) | Interno del proceso: solo el código Dart de la app puede llamarlo, y lee archivos de su propia caché |
| Cabeceras de caché nuevas | Solo para fotos de producto, que ya eran públicas; las respuestas de la API siguen con `no-store` |
| Ofuscación | Las compilaciones de release van con `--obfuscate` y los símbolos se guardan aparte |
| Secretos en el repositorio | `scripts/ci-security-check.mjs`: 1196 archivos revisados, sin secretos. Un aviso heredado: el certificado de prueba de SIFEN (`techstore-fe-test.p12`) debe rotarse y salir del repositorio |
| Claves de firma | `key.properties` y el keystore fuera de git |

**Riesgo residual — enlaces `techstore://`.** Un esquema propio puede registrarlo otra app, y el enlace de recuperación de contraseña lleva un token. El token vence en 15 minutos y sirve una sola vez, lo que acota el riesgo. La solución completa son los App Links de Android (enlaces `https` verificados con `assetlinks.json`), que requieren un dominio propio estable.
