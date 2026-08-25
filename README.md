# TechStore — Web + Mobile Flutter

TechStore es un sistema de comercio e inventario con dos clientes del mismo backend: una SPA Web React y una aplicación Android Flutter. PostgreSQL y Spring Boot son la única fuente de verdad para usuarios, permisos, catálogo, stock, ventas, compras, caja, fiscalidad y sincronización.

## Estado verificable

- Backend: Spring Boot 3.5.16, Java 21+, PostgreSQL 15, 45 migraciones Flyway.
- Web: React 18, Vite 8, TanStack Query, Zustand y STOMP.
- Mobile: Flutter 3.47.1 / Dart 3.13.1, Android 24–36, Riverpod, Dio, Drift y FCM.
- API canónica: OpenAPI 3.1 con 102 rutas y 125 operaciones.
- Última regresión: 328 unit + 44 integration backend, 14 Web y 21 Flutter, además de E2E auth/negocio/realtime/offline/update.

La entrega privada Android está completa: APK release firmada, instalada y actualizada en un Redmi Note 8 Pro con pruebas reales de backend, offline/reconexión, cámara y biometría. Google Play/Firebase quedan postergados para una publicación futura. Ver [estado persistente](docs/mobile-expansion/PROJECT_STATUS.md).

## Arquitectura

```text
Web React ─────┐
               ├── REST + STOMP ── Spring Boot ── PostgreSQL 15
Flutter Android┘                         │
                                  storage / FCM / correo
```

- Web y Mobile ejecutan comandos mediante la misma API `/api/v1`.
- Las reglas críticas y la autorización siempre se verifican en backend.
- Los cambios de dominio se guardan en un outbox transaccional y notifican por STOMP al tenant correcto.
- Los clientes invalidan caché y vuelven a consultar REST; un evento nunca sustituye el dato autoritativo.
- Mobile usa Drift solo para cache y cola offline idempotente, no como base paralela.

La descripción completa está en [ARCHITECTURE.md](docs/mobile-expansion/ARCHITECTURE.md), [API.md](docs/mobile-expansion/API.md) y [SYNC.md](docs/mobile-expansion/SYNC.md).

## Estructura

```text
techstore/
├── techstore-backend/       API, dominio, seguridad, Flyway y tests
├── techstore-frontend/      SPA Web React
├── techstore-mobile/        app Flutter Android y herramientas E2E
├── docs/api/                contrato OpenAPI canónico
├── docs/mobile-expansion/   auditoría, fases y operación
├── scripts/                 validación, export y release
├── docker/                  guards y configuración de contenedores
└── *.ps1                    operación local, backup y restauración
```

## Inicio local en Windows

El método recomendado usa el PostgreSQL portable y secretos externos al repositorio:

1. Copiar `secrets.local.ps1.ejemplo` como `secrets.local.ps1` y reemplazar todos los valores.
2. Configurar una vez el rol runtime con `configurar-rol-app.ps1` si aún no existe.
3. Iniciar y detener desde la raíz:

```powershell
.\iniciar-techstore.ps1 -SinNavegador
.\detener-techstore.ps1
```

El lanzador selecciona Java 21+, valida Node, inicia PostgreSQL, comprueba backups, ejecuta el backend con un rol sin superusuario/BYPASSRLS y levanta Vite. Solo registra y detiene procesos propios.

| Servicio | URL local |
|---|---|
| Web | `http://localhost:5173` |
| API | `http://localhost:8090` |
| Swagger | `http://localhost:8090/swagger-ui.html` |
| Health | `http://localhost:8090/actuator/health` |

Las credenciales se definen localmente; no se documentan passwords operativos ni se compilan en ningún cliente.

## Docker

Desarrollo:

```powershell
Copy-Item .env.example .env
# Completar secretos y valores requeridos.
docker compose --env-file .env --profile local up -d --build
```

Instalación comercial:

```powershell
Copy-Item .env.commercial.example .env.commercial
# Definir secretos fuertes, dominio HTTPS y perfil commercial.
docker compose --env-file .env.commercial --profile commercial up -d --build
```

El `config-guard` rechaza perfiles cruzados, placeholders y secretos inseguros. En modo comercial PostgreSQL/API quedan ligados a loopback y un proxy inverso del host publica únicamente la Web con HTTPS. Los volúmenes `postgres_data` y `uploads_data` deben respaldarse juntos.

## Desarrollo por componente

Backend:

```powershell
cd techstore-backend
$env:JAVA_HOME='C:\Program Files\Java\jdk-22'
..\tools\apache-maven-3.9.9\bin\mvn.cmd test
..\tools\apache-maven-3.9.9\bin\mvn.cmd verify
```

Web:

```powershell
cd techstore-frontend
npm ci
npm run check
```

Flutter:

```powershell
cd techstore-mobile
flutter pub get
flutter analyze --fatal-infos
flutter test
flutter run --flavor development -t lib/main_development.dart
```

Un teléfono físico necesita una URL LAN alcanzable y el launcher con `-PermitirRedLocal`; nunca usar esa opción en una red no confiable. Staging y Production exigen HTTPS.

## Configuración y secretos

- Backend: variables de entorno de `.env*`/`secrets.local.ps1`; nunca hardcodear JWT, DB, SMTP, Firebase o certificados.
- Web: `VITE_API_URL` solo contiene una URL pública/no secreta.
- Mobile: `--dart-define=API_BASE_URL=https://…`; access token en memoria y refresh token en Android Keystore.
- Firebase: `google-services.json` y cuenta de servicio permanecen fuera de Git.
- Firma: `techstore-mobile/android/key.properties` y el upload keystore permanecen fuera de Git.

Los controles y pendientes están en [SECURITY.md](docs/mobile-expansion/SECURITY.md).

## Offline, realtime y actualización

- Catálogo: red primero y último dato conocido como fallback.
- Carrito/preferencias: locales; se revalidan antes de checkout.
- Mensajes: cola FIFO idempotente con backoff y scope tenant/usuario.
- Stock, pagos, caja, ventas, compras y CRUD sensible: `ONLINE_ONLY`.
- Realtime: STOMP + catch-up REST por cursor; no hay polling permanente.
- Software Mobile privado: actualización instalando un APK con `versionCode` mayor y la misma firma, sin desinstalar.
- Software Mobile público futuro: Google Play In-App Updates flexible/inmediato según `/api/v1/app/version`.

## APK Android privada

Artefacto validado:

`artifacts/mobile/private/TechStore-Private-1.0.1+2.apk`

Para generar la siguiente versión:

```powershell
.\scripts\build-mobile-private.ps1 `
  -ApiBaseUrl http://IP-PRIVADA-PC:8090 `
  -VersionName 1.0.2 `
  -VersionCode 3
```

Ejecutar el preflight con el teléfono conectado:

```powershell
.\scripts\check-mobile-private-readiness.ps1 `
  -PhysicalNativeVerified `
  -PrivateUpdateVerified
```

El backend debe ejecutarse en la PC y el teléfono debe compartir la misma red. Respaldar `%LOCALAPPDATA%\TechStore\mobile-signing`: perder esa firma impide actualizar instalaciones existentes.

## Release público futuro

Con dominio y keystore reales:

```powershell
.\scripts\build-mobile-release.ps1 `
  -ApiBaseUrl https://api.techstore.tld `
  -VersionName 1.0.0 `
  -VersionCode 1
```

El pipeline público genera APK+AAB para Google Play cuando se decida publicar. Ver [RELEASE.md](docs/mobile-expansion/RELEASE.md).

## Contrato y pruebas E2E

```powershell
powershell -File scripts\export-openapi.ps1
node scripts\validate-openapi.mjs

cd techstore-mobile
$env:TECHSTORE_E2E_EMAIL='cuenta-unica@e2e.invalid'
$env:TECHSTORE_E2E_PASSWORD='password-de-prueba-fuerte'
dart run tool\auth_e2e.dart
dart run tool\business_e2e.dart
dart run tool\realtime_e2e.dart
```

Cada herramienta usa cuentas efímeras; deben eliminarse después de la ejecución. La matriz y los últimos resultados están en [TESTING.md](docs/mobile-expansion/TESTING.md).

## Troubleshooting

- Puerto ocupado: el launcher informa el PID y nunca mata procesos ajenos; cerrar el dueño o cambiar la configuración.
- Backend no inicia: revisar `%LOCALAPPDATA%\TechStore\run` y `pg.log`, secretos, Java 21+ y health check.
- Vitest no inicia workers en Windows: ejecutar `techstore-mobile\android\gradlew.bat --stop` y repetir con un worker de hilos.
- Mobile no conecta desde teléfono: confirmar misma LAN, firewall, `-PermitirRedLocal` y una `API_BASE_URL` alcanzable.
- Production no compila: es correcto si faltan `android/key.properties` o una URL HTTPS explícita.
- Realtime reconectado sin cambios: comprobar JWT/tenant y el catch-up `/api/v1/sync/events`.
- Migración falla: no editar una migración aplicada; crear la siguiente versión y validar desde una base vacía.

## Límites legales/operativos

La salida SIFEN actual es demostrativa: el certificado de prueba no acredita homologación ni transmisión a DNIT. Antes de operación comercial también se requieren revisión legal/fiscal, política de privacidad/Data Safety, backups externos con restauración ensayada, monitoreo y rotación de secretos.

La guía de operación Web permanece en [MANUAL.md](MANUAL.md); el historial técnico de la expansión está en [CHANGELOG.md](docs/mobile-expansion/CHANGELOG.md).
