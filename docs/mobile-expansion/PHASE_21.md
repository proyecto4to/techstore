# FASE 21 — APK FINAL PRIVADA

ESTADO: COMPLETED

## Artefacto

| Campo | Valor |
|---|---|
| VersionName | `1.0.1` |
| VersionCode | `2` |
| Build UTC | `2026-08-24T02:08:22.6380232Z` |
| Git HEAD registrado | `268047b61d12da67b83a87255103c35d7bf0b8fb` |
| Worktree al compilar | Con cambios; `gitDirty=true` registrado explícitamente |
| Environment | `private` |
| Distribution | `direct-apk` |
| Application ID | `py.com.techstore.app.private` |
| API | `http://192.168.100.8:8090` |
| APK | `artifacts/mobile/private/TechStore-Private-1.0.1+2.apk` |
| APK SHA-256 | `cb0feaa7dbc52bee5fc4e0200674902263d8cf8e8d36e522511d0fc9f5ebbe59` |
| Certificado SHA-256 | `c4a098390fc9f299a68dd51b79741ad931485ea012828e613afc7f801aabf0a2` |

Metadatos: `artifacts/mobile/private/private-release-metadata.json`.

## Verificación final

- [x] Analyzer y 21 tests normales aprobados; 2 E2E gated omitidos en suite común y aprobados por separado.
- [x] 477 entradas del APK escaneadas, 0 warnings del escáner de archivos.
- [x] Firma release válida y distinta de Android Debug.
- [x] Instalación física aprobada.
- [x] Inicio, registro/login, backend y catálogo aprobados.
- [x] Offline/reconexión y persistencia aprobados.
- [x] Cámara y biometría aprobadas.
- [x] Actualización directa desde `1.0.0+1` conservando sesión/carrito aprobada.
- [x] Cuenta, correo, refresh tokens y datos locales E2E eliminados.
- [x] `scripts/check-mobile-private-readiness.ps1` devuelve `PRIVATE_RELEASE_READY=True BLOCKED=0`.

## Instalación en otros Android

Copiar el APK al teléfono, permitir temporalmente “instalar aplicaciones desconocidas” para el explorador usado y abrir el archivo. Para actualizar, instalar una versión con `versionCode` mayor firmada con el mismo keystore; no desinstalar la anterior.

La PC debe mantener el backend en ejecución y accesible desde la misma red. Si cambia `192.168.100.8`, debe reservarse esa IP en el router o generarse una nueva APK con la dirección correcta.
