# PROJECT: TECHSTORE WEB + MOBILE

ULTIMA FASE: FASE 21 — APK final privada
FASE ACTUAL: FINALIZADA PARA DISTRIBUCIÓN PRIVADA
ESTADO: PROJECT_COMPLETED
AVANCE GLOBAL: 22/22 fases COMPLETED (100%)

## GOAL FINAL

- [ ] NO COMPLETADO
- [x] COMPLETADO — alcance vigente: APK privada por instalación directa, sin Google Play.

## FASES COMPLETADAS

- FASE 00 — Auditoría completa.
- FASE 01 — Mapa funcional.
- FASE 02 — Arquitectura objetivo.
- FASE 03 — Backend + API.
- FASE 04 — Tiempo real.
- FASE 05 — Proyecto Flutter.
- FASE 06 — Core Mobile.
- FASE 07 — Autenticación.
- FASE 08 — Funcionalidades de negocio.
- FASE 09 — Capacidades nativas.
- FASE 10 — Offline + sincronización.
- FASE 11 — Actualización de la app.
- FASE 12 — UI/UX.
- FASE 13 — Rendimiento.
- FASE 14 — Seguridad.
- FASE 15 — Testing.
- FASE 16 — Android real.
- FASE 17 — Release privado.
- FASE 18 — Documentación.
- FASE 19 — Auditoría final.
- FASE 20 — Prueba de integración total.
- FASE 21 — APK final privada.

## FASE ACTUAL

### Entrega privada cerrada

- APK: `artifacts/mobile/private/TechStore-Private-1.0.7+8.apk`.
- SHA-256: `ec80ab0489612052d5652a84e39275158983362d2fac2c0240ef7196010b2587`.
- Package: `py.com.techstore.app.private`.
- Backend LAN: `http://192.168.100.8:8090`.
- Dispositivos validados: Xiaomi Redmi Note 8 Pro, Android 11; Motorola moto g42, Android 13.
- Preflight: `PRIVATE_RELEASE_READY=True BLOCKED=0`.
- Módulo Facturas: lista paginada, detalle, PDF (abrir/compartir/imprimir), deep link y refresco en tiempo real; clientes limitados en backend a sus documentos.
- Login Mobile: fondo gráfico original de TechStore, contraste adaptado y formulario responsive accesible.

## BLOQUEOS

- Ninguno para instalar y usar la APK privada en dispositivos Android de la misma red.

## PROBLEMAS CRÍTICOS

- Ninguno dentro del alcance privado actual.

## LIMITACIONES / POSTERGADO

- Firebase/FCM no está configurado: push real permanece desactivado.
- Google Play, AAB público, App Links y dominio HTTPS quedan para una futura publicación.
- SIFEN continúa demostrativo/no homologado; se requiere homologación antes de uso fiscal legal.
- El navegador integrado no estuvo disponible (`[]`); React quedó cubierto por lint, 14 tests, build e integración REST/STOMP.
- La APK usa la IP LAN `192.168.100.8`; conviene reservarla en el router.
- El build registra `gitDirty=true`; el artefacto es verificable por hash, pero se recomienda crear un commit de release cuando el usuario lo autorice.
- La versión `1.0.7+8` quedó instalada físicamente en el Motorola moto g42 y convive con la aplicación GeneXus gracias a sus identificadores de paquete distintos.

## ÚLTIMAS PRUEBAS

- Backend: suite completa y pruebas nuevas de acceso a facturas por propietario, 0 fallos.
- Web: lint + 14/14 tests + build, 0 fallos.
- Flutter: analyze limpio + 23 tests aprobados; 2 E2E condicionados omitidos sin su ambiente externo.
- OpenAPI: 102 rutas / 125 operaciones válido; Mobile sin discrepancias.
- APK privada: archivo y firma verificados, certificado no-debug, escaneo sin incidencias.
- APK `1.0.7+8`: build release ofuscado, firma y seguridad PASS; configurada para el backend LAN vigente.
- APK `1.0.7+8` en Motorola moto g42: instalación ADB, arranque en frío, catálogo público, detalle e imagen real de producto, PASS; sin ANR ni errores fatales.
- Imágenes del catálogo: `37/37` rutas responden HTTP 200 con contenido `image/*`; las 33 fotos empaquetadas y las 4 subidas fueron verificadas desde la LAN, y Flutter mostró correctamente una ruta antes bloqueada.
- Instalación `1.0.0+1` y actualización `1.0.1+2` sin desinstalar: PASS.
- Registro/login, catálogo, carrito, sesión y segundo plano: PASS.
- Offline con caché, banner y reconexión: PASS después de corregir el sondeo del backend.
- Biometría real: activación y desbloqueo al reanudar, PASS.
- Cámara real: picker, permiso y cámara MIUI abiertos; cancelado sin mutar producto, PASS.
- Datos físicos E2E: cuenta, 3 refresh tokens, correo y datos locales eliminados.

## SIGUIENTE ACCIÓN

- Ninguna para la entrega privada actual; mantener el teléfono en la misma red que `192.168.100.8:8090` mientras el backend sea local.

## CONTROL DE FASES

| Fase | Nombre | Estado |
|---:|---|---|
| 00 | Auditoría completa | COMPLETED |
| 01 | Mapa funcional | COMPLETED |
| 02 | Arquitectura objetivo | COMPLETED |
| 03 | Backend + API | COMPLETED |
| 04 | Tiempo real | COMPLETED |
| 05 | Proyecto Flutter | COMPLETED |
| 06 | Core Mobile | COMPLETED |
| 07 | Autenticación | COMPLETED |
| 08 | Funcionalidades de negocio | COMPLETED |
| 09 | Capacidades nativas | COMPLETED |
| 10 | Offline + sincronización | COMPLETED |
| 11 | Actualización de la app | COMPLETED |
| 12 | UI/UX | COMPLETED |
| 13 | Rendimiento | COMPLETED |
| 14 | Seguridad | COMPLETED |
| 15 | Testing | COMPLETED |
| 16 | Android real | COMPLETED |
| 17 | Release privado | COMPLETED |
| 18 | Documentación | COMPLETED |
| 19 | Auditoría final | COMPLETED |
| 20 | Prueba de integración total | COMPLETED |
| 21 | APK final privada | COMPLETED |
