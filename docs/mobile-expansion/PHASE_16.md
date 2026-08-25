# FASE 16 — ANDROID REAL

ESTADO: COMPLETED

## Dispositivo

- Xiaomi Redmi Note 8 Pro (`begonia`), Android 11 / API 30, arm64.
- Serial ADB: `zl89pnl78txgwwiv`.
- APK privada: `py.com.techstore.app.private`, versión `1.0.1+2`.
- Backend por LAN: `http://192.168.100.8:8090`.

## Pruebas físicas — 2026-08-23

| Prueba | Resultado |
|---|---|
| Autorización ADB e instalación | PASS |
| Inicio, registro, login y navegación | PASS |
| Catálogo/backend real | PASS |
| Carrito y persistencia | PASS |
| Segundo plano y reanudación de sesión | PASS |
| Sin Wi-Fi/datos: banner offline y caché | PASS |
| Reconexión: banner retirado y backend HTTP 200 | PASS |
| Biometría: activación y bloqueo al reanudar | PASS; dos huellas aceptadas por el sensor real |
| Cámara desde Administración → Productos | PASS; cámara MIUI y obturador abiertos, cancelado sin alterar el producto |
| Permiso Android de cámara | PASS |
| Actualización `1.0.0+1` → `1.0.1+2` | PASS; sin desinstalar, conservó sesión y carrito |
| Memoria en login | 124.515 KB PSS; sin ANR ni crash |
| Crash/errores Flutter/cleartext | PASS; no se detectaron errores |

## Defecto físico corregido

La primera versión confiaba en que Android tuviera algún transporte de red y no comprobaba si el backend era alcanzable. En el dispositivo real no mostraba el banner offline. `ConnectivityBanner` ahora sondea el backend al cambiar la conectividad y al reanudar la app; la versión `1.0.1+2` aprobó offline y reconexión.

## Limpieza

La cuenta efímera `@e2e.invalid`, sus tres sesiones refresh y su correo de bienvenida fueron eliminados. Los datos locales de prueba de la APK también se limpiaron. La app permanece instalada y abre en una pantalla de login limpia.

## Alcance privado

Firebase/FCM y Google Play no son requisitos de esta distribución directa. Push real queda desactivado hasta que el usuario decida configurar Firebase; no afecta catálogo, ventas, sincronización ni instalación privada.
