# CHANGELOG — MOBILE EXPANSION

## 2026-08-30

- Instalada y validada `TechStore-Private-1.0.7+8.apk` en un Motorola moto g42 con Android 13 mediante ADB; inicio, catálogo público, detalle e imagen real de producto cargaron desde `http://192.168.100.8:8090` sin ANR ni errores fatales.
- Confirmada la convivencia de Flutter (`py.com.techstore.app.private`) y GeneXus (`com.genexus.techstoremobilegx.techstorelogin`) en el mismo dispositivo.
- Corregidas las 33 fotos del catálogo que desde Mobile devolvían HTTP 401: los recursos reales y acreditados quedaron empaquetados en el backend y `/img/productos/**` se habilitó como lectura pública. Auditoría HTTP `37/37 PASS` y validación visual del Ryzen en el moto g42 aprobada.

## 2026-08-26

- Rediseñado el login Flutter con una ilustración vertical original de TechStore, gradiente de contraste, identidad visual y formulario responsive sobre tarjeta translúcida.
- Conservados los flujos de acceso, recuperación, catálogo público y registro; añadida cobertura automática para verificar la carga del fondo.
- Regresión Flutter aprobada: analyzer sin incidencias y 23 pruebas exitosas; 2 E2E condicionadas omitidas al no contar con sus servicios externos.
- Generada `TechStore-Private-1.0.7+8.apk` para `http://192.168.100.8:8090`; SHA-256 `ec80ab0489612052d5652a84e39275158983362d2fac2c0240ef7196010b2587`, firma release y escaneo de seguridad aprobados. Instalación física pendiente porque ADB no detectó el dispositivo.

## 2026-08-24

- Reemplazada la vista técnica genérica de Facturas en Flutter por un módulo nativo con lista paginada, estados, receptor, importes, detalle y PDF para abrir, compartir o imprimir.
- Ampliada la API compartida para que el cliente consulte exclusivamente las facturas de sus pedidos; Admin/Cajero conservan acceso global. Listado, detalle y PDF verifican propiedad en backend.
- Añadidos deep links a detalle, invalidación por eventos de factura/pago y pruebas de contrato/seguridad.
- Generada `TechStore-Private-1.0.2+3.apk` para `http://192.168.100.8:8090`; SHA-256 `ff30bd3958ca11f821718a2c6e4f3793b1a4ddc6e549354e716e3386e69641b2`, firma y escaneo de seguridad aprobados.
- Backend local reconstruido, reiniciado con acceso LAN y contrato OpenAPI reexportado/validado (102 rutas, 125 operaciones).

## 2026-08-23

- Añadido el flavor Android `private`, firma release externa, build reproducible, metadatos, símbolos y preflight específico para distribución APK directa.
- Generada e instalada `TechStore-Private-1.0.1+2.apk`; firma/hash verificados y actualización desde `1.0.0+1` aprobada sin pérdida de sesión o carrito.
- Completadas pruebas en Redmi Note 8 Pro: login/navegación, backend LAN, offline/reconexión, segundo plano, biometría y cámara/permisos.
- Corregido `ConnectivityBanner` después de detectar físicamente que un transporte Android activo no garantizaba acceso al backend.
- Eliminados de forma verificada la cuenta, tres refresh tokens, correo y datos locales usados en la prueba física.
- Fases 16, 17, 20 y 21 completadas para el alcance privado; Google Play/Firebase se registran como publicación futura opcional.
- Completada la integración total automatizable sobre PostgreSQL descartable: 12 grupos de dominio, ambas direcciones Web/Mobile por REST+STOMP, aislamiento tenant, offline/reconexión e idempotencia.
- Añadido un arnés seguro para crear/reiniciar/eliminar el backend y la base aislados de Fase 20, más un artefacto Maven con classifier que no interfiere con el entorno local.
- Añadidos E2E reales para matriz funcional, sincronización cruzada, cola offline y política de actualización optional/mandatory.
- Corregido el contrato de creación de chat en Flutter (`conversacion.id`) y el mapeo backend de parámetros requeridos/tipos/constraints a HTTP 400.
- Aprobada la regresión: backend 328+44, Web 14/14+lint/build, Flutter analyzer+20 tests normales y 2 E2E gated.
- La integración automatizable de Fase 20 quedó cerrada antes de las pruebas físicas privadas.
- Añadido preflight de Release Mobile para validar URL/DNS/TLS, keystore/alias, Firebase package, ADB, Git y evidencias manuales de Play/App Links/FCM/smoke test sin imprimir secretos.

## 2026-08-22

- Completadas Fases 00–08: auditoría, mapa funcional, arquitectura, API, realtime, scaffold Flutter, Core, autenticación y negocio Mobile.
- Implementados WebSocket/STOMP multi-tenant, catch-up REST e invalidación reactiva Web/Mobile.
- Implementados catálogo, carrito, checkout, direcciones, pedidos, tracking, cuenta, favoritos, notificaciones, chat y módulos operativos/administrativos.
- Añadidas herramientas E2E de autenticación, sincronización y negocio.
- Regresión completa Web/backend/Flutter aprobada y APK Development generado.
- Añadidas capacidades nativas Android: cámara/galería, recorte y compresión, biometría, FCM/deep links, apertura/compartir/impresión y exportación DNIT.
- Los comprobantes físicos de compra ahora pueden adjuntarse y consultarse de forma privada desde Web y Mobile.
- Implementado modo offline seguro: catálogo con fallback local, carrito local, indicador de conectividad y cola idempotente de mensajes aislada por tenant/usuario.
- Implementada actualización Mobile segura: política de compatibilidad backend, Play Core flexible/inmediato y fallback exclusivo a Google Play HTTPS.
- Unificada la UX Mobile con estados async accesibles, grillas adaptativas, semántica, tooltips y pruebas con texto 2×.
- Optimizado rendimiento: rutas Web lazy (chunk inicial 234,7 KB), imágenes Mobile redimensionadas en decode y APK release arm64 medido en 22,4 MB.
- Completada auditoría de seguridad multicapa: manifests endurecidos, permisos mínimos, SBOM/licencias revisados y escaneo de repo/APK sin secretos.
- Consolidada la Fase 15: 327 unit + 44 integration backend, 14 Web, 19 Flutter y tres flujos E2E reales aprobados; arnés de negocio y aislamiento del outbox de pruebas corregidos.
- Preparado Release Android: identidad visual propia, adaptive icon/splash, firma fail-closed, script reproducible y builds APK/AAB de auditoría; publicación sigue bloqueada por activos externos.
- Completada la documentación integral: README monorepo actualizado, guías API/sync/security/testing/release coherentes, links validados y OpenAPI normalizado/reexportado.
- Completada la auditoría final automatizable: matriz explícita de 67 funciones, 91 rutas Mobile cruzadas con OpenAPI sin discrepancias y bloqueos físico/externos separados.
