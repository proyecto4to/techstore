# FASE 11 — ACTUALIZACIÓN DE LA APP

ESTADO: COMPLETED

## Estrategia

El canal oficial es Google Play con Android App Bundle. TechStore no descarga, verifica ni instala APK arbitrarios. Play App Signing conserva la identidad de firma y Play Core entrega el artefacto autorizado.

- Actualización opcional: flujo flexible; el usuario puede continuar y elegir «Más tarde».
- Actualización obligatoria: flujo inmediato cuando `versionCode < minimumVersionCode`.
- Fallback: abrir exclusivamente la URL HTTPS configurada de Google Play.
- Build sideloaded/dev: Play Core puede no estar disponible; se usa el fallback sin declarar error fatal.
- Sin conexión: se reutiliza la última política conocida, recalculada contra el `versionCode` instalado para evitar bloquear una app ya actualizada.

## Contrato backend

`GET /api/v1/app/version?platform=android&versionCode={n}` es público y devuelve:

- `minimumVersionCode`: versión mínima compatible.
- `latestVersionCode`: última versión publicada/configurada.
- `updateAvailable` y `mandatory` calculados por el backend.
- `distribution=GOOGLE_PLAY`.
- `storeUrl` HTTPS validada al iniciar el backend.

Variables operativas: `MOBILE_MIN_VERSION_CODE`, `MOBILE_LATEST_VERSION_CODE` y `MOBILE_PLAY_STORE_URL`. El mínimo solo debe elevarse después de que la versión compatible esté disponible para toda la audiencia necesaria.

## Mobile

- `package_info_plus` obtiene `versionCode` real.
- `in_app_update` integra Play Core oficial.
- El gate no bloquea arranque mientras consulta y solo impide uso si la política es obligatoria.
- Se distinguen flujos inmediato, flexible y Store mediante una decisión pura testeada.
- No existen permisos de instalación de paquetes ni almacenamiento de APK.

## Pruebas

- [x] Política backend: versión antigua obligatoria, compatible opcional y actual sin update.
- [x] Endpoint público real: HTTP 200 con contrato esperado.
- [x] OpenAPI válido: 102 rutas / 125 operaciones.
- [x] Decisión Mobile: inmediato, flexible y fallback Store.
- [x] Flutter analyze sin incidencias.
- [x] Suite Flutter completa: 17/17.
- [x] APK Development debug compila con Play Core.
- [ ] Entrega Play real v1 → v2: reservada a pista interna y dispositivo físico en Fases 16/20.

## Referencias

- Android Developers, In-app updates: https://developer.android.com/guide/playcore/in-app-updates
- Android Developers, Kotlin/Java update flows: https://developer.android.com/guide/playcore/in-app-updates/kotlin-java
