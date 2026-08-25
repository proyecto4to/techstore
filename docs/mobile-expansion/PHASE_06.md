# FASE 06 — Core Mobile

ESTADO: COMPLETED

## Resultado

- Routing declarativo con `go_router` y fallback para rutas inválidas.
- Tema Material 3 claro/oscuro y componentes reutilizables de carga, vacío y error.
- Dependency injection y estado base con Riverpod.
- Cliente Dio con timeouts, correlación, verbos CRUD, descarga y logging sin cuerpos ni credenciales.
- Errores RFC 7807 transformados a fallos tipados de red, autenticación, permiso, validación, conflicto y servidor.
- Almacenamiento seguro respaldado por el plugin de Android Keystore.
- Base Drift/SQLite con código generado, caché con TTL y aislamiento por scope tenant/usuario.
- Servicio de conectividad que diferencia interfaz disponible de backend realmente alcanzable.
- HTTP no cifrado habilitado únicamente para el flavor Development; Staging y Production exigen HTTPS.

## Criterios de aceptación

- [x] Routing y tema estables.
- [x] Componentes base accesibles.
- [x] API client y networking testeables.
- [x] Estado y DI configurados.
- [x] Logging con redacción de secretos.
- [x] Error handling tipado.
- [x] Secure storage y local storage disponibles.
- [x] Connectivity y cache disponibles.
- [x] Configuración central por flavor.
- [x] Base Flutter compila y no tiene issues de análisis.
- [x] Sin pendientes críticos de fase.

## Pruebas

- `dart format --output=none --set-exit-if-changed lib test`: OK.
- `flutter analyze --fatal-infos`: sin issues.
- `flutter test`: 8/8 aprobadas.
- Caché: aislamiento de scope y expiración verificados sobre SQLite real en memoria.
- API: header de correlación y mapeo de Problem Details verificados.
- `flutter build apk --debug --flavor development`: OK.
- Escaneo de repositorio: OK; permanece únicamente la excepción SIFEN heredada ya documentada.

## Nota operativa

El proceso backend no estaba activo durante la comprobación opcional de `/actuator/health`. No afecta al Core aislado; la autenticación end-to-end de la Fase 07 levantará backend y PostgreSQL reales antes de sus pruebas.
