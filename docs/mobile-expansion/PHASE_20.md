# FASE 20 — PRUEBA DE INTEGRACIÓN TOTAL

ESTADO: COMPLETED

## Integración automatizada

El 2026-08-23 se ejecutó una base PostgreSQL descartable, backend real y clientes REST/STOMP independientes. Al finalizar se detuvo el entorno y se eliminaron sus datos.

| Escenario | Resultado |
|---|---|
| Autenticación, refresh rotativo y logout | PASS |
| Web → backend → Mobile por REST/STOMP | PASS |
| Mobile → backend → Web por REST/STOMP | PASS |
| Aislamiento tenant y recuperación realtime | PASS |
| Productos, usuarios, empresa y permisos | PASS |
| Proveedores, ofertas, compras y comprobantes | PASS |
| Clientes, carrito, checkout, ventas y stock | PASS |
| Caja, facturación, PDF, reportes y contabilidad | PASS |
| Mensajería, notificaciones y auditoría | PASS |
| Offline, cola Drift, reconexión e idempotencia | PASS |
| Política de actualización optional/mandatory | PASS |

## Integración física privada

| Escenario | Resultado |
|---|---|
| Android → backend LAN | HTTP 200 / backend `UP` |
| Registro/login y catálogo real | PASS |
| Caché y carrito sin conexión | PASS |
| Reconexión y actualización de UI | PASS |
| Cámara y permiso Android | PASS |
| Biometría y bloqueo al reanudar | PASS |
| Instalación directa v1 → v2 | PASS; conservó datos |
| Inicio limpio después de retirar datos E2E | PASS |

## Regresión final

- Backend: 328 unitarias + 44 integraciones, 0 fallos.
- Web: lint, 14/14 tests y build de producción, 0 fallos.
- Flutter: analyzer limpio; 20 tests normales aprobados y 2 E2E gated aprobados por separado contra backend real.
- OpenAPI: 102 rutas y 125 operaciones válidas; uso Mobile sin discrepancias.
- Seguridad: escaneo fuente/APK sin errores; SIFEN continúa marcado como demostrativo.

## Exclusiones deliberadas

- Push FCM real: desactivado porque el usuario no configuró Firebase y no lo requiere para la APK privada.
- Google Play/App Links: no aplican a instalación directa.
- Control visual React con el navegador integrado: el runtime reportó `[]`; la Web quedó cubierta por tests, lint, build y la integración de clientes independientes, por lo que no es un pendiente crítico del alcance privado.

Los datos físicos efímeros se eliminaron de la base normal y del dispositivo.
