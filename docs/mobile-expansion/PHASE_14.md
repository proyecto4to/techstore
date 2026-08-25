# FASE 14 — SEGURIDAD

ESTADO: COMPLETED

## Controles verificados

### Backend / API / Database

- JWT corto en memoria, refresh rotativo/revocable y recovery token de un uso.
- Roles/permisos se aplican en backend; el cliente nunca es autoridad.
- Tenant firmado, RLS forzado y contexto transaccional aíslan datos.
- Problem Details no filtra stack traces; correlation IDs no contienen secretos.
- Uploads validan tamaño, tipo real/magic bytes y nombre UUID interno.
- Comprobantes privados requieren auth; solo imágenes de catálogo usan ruta pública.
- CORS, actuator y WebSocket tienen reglas explícitas; STOMP restringe topic por tenant.

### Web

- Token de acceso permanece en memoria; refresh usa cookie/contrato protegido existente.
- No hay credenciales demo en producción ni secretos en variables `VITE_*`.
- `npm audit --omit=dev`: 0 vulnerabilidades.
- SBOM CycloneDX: 308 componentes, 0 licencias sin resolver.

### Flutter / Android

- Refresh token en Android Keystore mediante `flutter_secure_storage`; access token en memoria.
- Logs redactan authorization/password/token/secret/cookie y no vuelcan bodies.
- Producción exige API HTTPS; manifest release fija `usesCleartextTraffic=false` y `allowBackup=false`.
- HTTP se habilita únicamente en flavor development para `10.0.2.2`.
- Permisos de audio/video transitivos eliminados. Release conserva solo Internet, red, cámara/imágenes, biometría, notificaciones/FCM y wake lock requeridos.
- No existe `REQUEST_INSTALL_PACKAGES`, keystore, cuenta de servicio, password o API key dentro del APK.
- APK release inspeccionado: 449 entradas, sin formatos/patrones sensibles.

## Supply chain

- Scanner de repositorio: 774 archivos, 0 errores, 1 excepción SIFEN conocida.
- SBOM backend: 211 componentes, 0 licencias sin resolver.
- Workflows fijan actions por SHA y ejecutan tests, SBOM, licencia y escaneo de artefactos.
- `flutter_secure_storage` 11 fue evaluado y revertido: requiere compileSdk 37, incompatible con el máximo estable de AGP 9.1 (36). Se mantiene 10.0.0 hasta actualizar la toolchain como conjunto.

## Hallazgos residuales controlados

1. `techstore-fe-test.p12` es un certificado SIFEN heredado/demostrativo. Debe rotarse y externalizarse antes de homologación fiscal; ya está permitido únicamente como excepción explícita del scanner.
2. Password reset usa custom scheme, adecuado para desarrollo pero menos fuerte que Android App Links verificados. Producción debe asignar dominio real y `assetlinks.json` en Fase 17 antes del release público.
3. El APK de auditoría usa URL HTTPS ficticia y firma local; no es el APK final. URLs/keystore reales son controles de Fase 17/21.

## Pruebas

- [x] Repository/secret/workflow scan OK.
- [x] npm audit 0 vulnerabilidades.
- [x] SBOM + licencias Web/backend OK.
- [x] Flutter analyze + 19/19 tests tras cambios de seguridad.
- [x] APK production release recompilado (23.442.317 bytes).
- [x] Manifest/APK verificados: backup y cleartext deshabilitados, permisos mínimos, sin secretos.
