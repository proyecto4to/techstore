# FASE 18 — DOCUMENTACIÓN

ESTADO: COMPLETED

## Entregables revisados

- `README.md`: reescrito para el monorepo Web + Flutter real, stack/versiones actuales, inicio local/Docker, configuración, pruebas, offline, realtime, release y troubleshooting.
- `techstore-mobile/README.md`: desarrollo por flavor, URL LAN, calidad y release production mediante el script seguro.
- `ARCHITECTURE.md`: componentes, feature-first, auth, realtime, offline, FCM, seguridad, CI/CD y toolchain Android 36.
- `API.md`: OpenAPI canónico, auth/tenant, recovery, version policy y comprobantes privados.
- `SYNC.md`: outbox transaccional, STOMP, catch-up REST, deduplicación, cursor y clasificación offline implementada.
- `SECURITY.md`: controles auditados y límites concretos de publicación.
- `TESTING.md`: comandos, conteos de regresión y matriz físico/externo.
- `RELEASE.md`: identidad, firma, build reproducible, Play Update y checklist.
- `CHANGELOG.md`: fases y cambios materiales hasta Release.

## Correcciones documentales

- Eliminadas afirmaciones obsoletas sobre Spring Boot 3.2, 42 migraciones y una app Expo externa como Mobile vigente.
- Eliminadas credenciales demo del README; los accesos operativos se configuran fuera de Git.
- ROADMAP sincronizado con fases 00–15 completas y bloqueos 16/17.
- Contrato OpenAPI reexportado para restaurar servidor canónico relativo `/`.
- Script release endurecido para rechazar también dominios documentales `.tld`.

## Validación

- [x] 35 archivos Markdown recorridos; 0 enlaces locales rotos.
- [x] OpenAPI 3.1 válido: 102 rutas, 125 operaciones y servidor relativo.
- [x] Health backend `UP` y Web HTTP 200 durante validación de comandos.
- [x] Script release rechaza URL `.tld`/placeholder antes de compilar.
- [x] Instalación, configuración, backend, API, DB, sync, offline, update, APK y troubleshooting cubiertos.
- [x] Bloqueos externos descritos sin atribuir resultados inexistentes.

## Criterios de aceptación

- [x] README, Architecture, API, Sync, Security, Testing, Release y Changelog completos.
- [x] Comandos y rutas coherentes con el repositorio actual.
- [x] Documentación navegable y sin pendientes críticos propios de esta fase.
