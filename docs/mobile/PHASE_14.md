# Fase 14 — CI y cadena de suministro

**Estado:** completada y validada  
**Fecha de cierre:** 2026-08-13  
**Alcance:** CI reproducible para backend, web y mobile, SAST opt-in, dependency audit, SBOM, licencias y escaneo de secretos/artefactos.

## Resultado

El workflow de GitHub Actions cubre los tres productos con instalaciones reproducibles y gates equivalentes a los ejecutados localmente. No recibe secretos de producción, no genera binarios firmados y no usa los puertos reservados `8080`/`8181`.

- Backend: Java 21, unitarias e integraciones PostgreSQL mediante `clean verify`.
- Web: Node 22, `npm ci`, lint, Vitest y build de producción sin credenciales demo visibles.
- Mobile: Node 22, TypeScript, lint, Jest, validador Maestro, Expo config, Doctor y export Android Hermes con API `8090`.
- Supply chain: auditoría crítica de dependencias de producción, tres SBOM CycloneDX y revisión de licencias.
- Seguridad: secret scan propio, inspección de JAR/bundles, acciones fijadas a SHA, permisos mínimos, timeout y concurrency.
- Mantenimiento: Dependabot semanal para Actions, Maven, frontend y mobile.
- SAST: CodeQL para Java y JavaScript/TypeScript; en este repositorio privado requiere GitHub Advanced Security y la variable `ENABLE_CODEQL=true`.

La política y el procedimiento están en `docs/security/CI_SUPPLY_CHAIN.md`.

## Evidencia de validación

| Control | Resultado |
| --- | --- |
| Backend compile | Aprobado con Java 22 local; CI fija Java 21 del proyecto. |
| Backend unitario | 51 suites, 315/315 pruebas. |
| Backend integración | 8 suites, 37/37 pruebas. |
| Migraciones PostgreSQL 15 | `MigracionesIT` 15/15; 42 migraciones validadas. |
| Frontend web | ESLint, 6 archivos/12 pruebas y build aprobados. |
| Mobile | TypeScript y ESLint aprobados; 21 suites/52 pruebas. |
| Maestro estático | 5 YAML, 31 selectores y 12 variables validados. |
| Expo Doctor | 20/20 comprobaciones. |
| Export Android | Hermes 5,4 MB; 50 archivos/17.506.234 bytes inspeccionados. |
| Dependencias | 0 vulnerabilidades críticas en web y mobile producción. |
| SBOM/licencias | Backend 109 componentes; web+mobile 1.381; sin licencias rechazadas ni desconocidas. |
| Secret scan | Aprobado con un warning por el certificado SIFEN heredado ya documentado. |
| Puertos | API/WS `8090`, nginx comercial `8091`, Metro `8081`; sin uso activo de `8080`/`8181`. |

`mvn clean verify` local ejecutó todo el código y produjo reportes verdes, pero el empaquetado final no pudo reemplazar un JAR previo retenido por Windows/OneDrive. Se confirmó por separado `compile`, 315 unitarias, 37 integraciones y el escaneo del JAR existente. El runner Linux parte de un checkout limpio y no comparte ese bloqueo local; el workflow conserva `mvn -B clean verify` como gate real.

## Archivos principales de esta fase

- `.github/workflows/ci.yml`
- `.github/dependabot.yml`
- `.gitignore`
- `techstore-mobile/.gitignore`
- `scripts/ci-security-check.mjs`
- `scripts/review-sbom-licenses.mjs`
- `docs/security/CI_SUPPLY_CHAIN.md`
- `docs/security/SECURITY_HARDENING.md`
- `docs/mobile/PHASE_14.md`
- `README.md`
- `techstore-mobile/README.md`

## Nueva migración

Ninguna. La Fase 14 no modifica el modelo de datos.

## Pendientes

- Habilitar GitHub Advanced Security y `ENABLE_CODEQL=true` si la licencia/plan del repositorio privado lo permite.
- Corregir las vulnerabilidades altas cuando Expo/Vite publiquen actualizaciones compatibles; no aplicar el downgrade forzado sugerido por npm.
- Rotar y externalizar el certificado SIFEN heredado con la autoridad correspondiente, luego eliminar su excepción del scanner.
- Configurar branch protection para exigir los jobs del workflow una vez que estos archivos lleguen al remoto.
- El APK candidato, AAB y preparación iOS se resolvieron técnicamente en la [Fase 15](PHASE_15.md); resta la aceptación manual en dispositivo.

## Criterios de aceptación

- [x] Backend, web y mobile tienen gates CI reproducibles.
- [x] Mobile ejecuta typecheck, lint, Jest, Doctor, config y export Android.
- [x] Secret scan, dependency audit, SBOM, licencias y artefactos están automatizados.
- [x] Actions fijadas por SHA, permisos mínimos, timeout y concurrency.
- [x] Dependabot cubre todos los ecosistemas del repositorio.
- [x] Regresión local de código, tests, migraciones, Doctor y export en verde.
