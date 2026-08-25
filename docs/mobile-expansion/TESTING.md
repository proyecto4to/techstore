# TESTING

ESTADO: COMPLETED para distribución APK privada; validaciones públicas externas postergadas.

## Capas y comandos

- Backend: `mvn clean verify` ejecuta JUnit unitario y Failsafe sobre PostgreSQL 15 embebido con Flyway real.
- Web: `npm run lint`, `npx vitest run --pool=threads --maxWorkers=1` y `npm run build`.
- Flutter: `flutter analyze` y `flutter test`.
- E2E local: `dart run tool/auth_e2e.dart`, `business_e2e.dart` y `realtime_e2e.dart` contra `http://localhost:8090`.
- E2E total aislado: `scripts/phase20-isolated-environment.ps1`, `integration_total_e2e.dart` y `cross_client_sync_e2e.dart` contra una base descartable y backend `8092`.
- E2E Flutter gated: `offline_reconnect_e2e_test.dart` y `update_policy_e2e_test.dart`; requieren variables de entorno explícitas y no corren accidentalmente en la suite general.
- Android release: build, `apkanalyzer`, escaneo del ZIP y manifest/permisos.
- Android privado: `scripts/check-mobile-private-readiness.ps1` valida backend LAN, keystore, hash/firma APK, versión instalada y evidencia física.

## Integración total automatizable — 2026-08-23

- Backend: 328 unit tests + 44 integration tests; 0 fallos.
- Web: lint, 14 tests y build de producción; 0 fallos.
- Flutter: analyzer limpio, 20 tests normales aprobados; 2 E2E gated aprobados por separado contra backend real.
- E2E total: 12 grupos de dominio incluyendo CRUD, stock, checkout, compras, caja, facturación/PDF, contabilidad, archivos privados, notificaciones, auditoría y sync.
- Cross-client: `PRODUCT_CREATED` Web → Mobile y `PRODUCT_UPDATED` Mobile → Web por STOMP, refetch REST, catch-up y aislamiento tenant.
- Offline/reconexión: una operación persistida en Drift, scope aislado, drenado posterior y ningún duplicado.
- Actualización: ramas optional y mandatory de `AppUpdateService` aprobadas con política real del backend.
- Seguridad/API: OpenAPI 102/125, uso Mobile sin discrepancias y escaneo de 812 archivos sin errores.

El perfil Maven `phase20-isolated` genera un JAR con classifier para no reemplazar el backend local durante las pruebas destructivas. El script solo elimina bases cuyo nombre y PID pasan sus validaciones estrictas.

## Última regresión completa — 2026-08-22

- Backend: 327 unit tests + 44 integration tests; 0 fallos; 45 migraciones.
- OpenAPI 3.1: 102 rutas y 125 operaciones.
- Web: lint, 14 tests y build de producción; 0 fallos.
- Flutter: analyzer limpio y 19 tests; 0 fallos.
- E2E auth: registro/login/refresh rotativo/logout.
- E2E negocio: catálogo/filtro, favoritos CRUD, carrito y direcciones CRUD.
- E2E realtime: `USER_UPDATED` por STOMP y aislamiento por tenant.
- E2E cleanup: 4 usuarios y 8 sesiones eliminados; 0 residuos.

## Entorno Windows

Vitest se ejecuta con un worker de hilos. Si Gradle dejó daemons de builds Android, ejecutar `android/gradlew.bat --stop` antes de Vitest evita timeouts de creación de workers sin reducir cobertura.

## Android físico privado — 2026-08-23

- Xiaomi Redmi Note 8 Pro, Android 11 / API 30, arm64.
- Instalación limpia, registro/login, navegación, catálogo y carrito: PASS.
- Segundo plano/reanudación y actualización `1.0.0+1` → `1.0.1+2` conservando datos: PASS.
- Wi-Fi/datos deshabilitados: caché y banner offline; reconexión posterior: PASS.
- Biometría real: activación y desbloqueo al reanudar, PASS.
- Cámara real: permiso, app MIUI y obturador abiertos desde Productos; cancelado sin mutar datos, PASS.
- APK final, certificado y SHA-256: PASS; preflight privado `7/7`.
- Cuenta, tres sesiones refresh, correo y datos locales E2E eliminados.

## Postergado para publicación pública

- Firebase real: entrega push, token y deep link desde notificación.
- Google Play: AAB, pista controlada y flujo Play v1 → v2.
- Firma de producción y dominio verificado: se validan en Release.
- Navegador controlable: interacción React visual; el entorno actual no expuso ningún navegador al controlador integrado.

Las cuentas E2E usan exclusivamente `@e2e.invalid` y deben eliminarse al finalizar cada ejecución.
