# CI y cadena de suministro

**Vigente desde:** 2026-08-13  
**Workflow:** `.github/workflows/ci.yml`

## Qué bloquea una integración

Cada push o pull request a `main` ejecuta jobs independientes y sin secretos de producción:

- Repositorio: nombres de credenciales, claves/certificados, marcadores de secretos, uso activo de puertos reservados y actions sin SHA completo.
- Backend: Java 21, `mvn clean verify`, SBOM CycloneDX, política de licencias e inspección del JAR.
- Frontend: `npm ci`, ESLint, Vitest, build, `npm audit` de producción, SBOM, licencias e inspección de `dist`.
- SAST: CodeQL para Java/Kotlin y JavaScript/TypeScript. En repositorios privados se ejecuta al definir la variable de repositorio `ENABLE_CODEQL=true`, porque depende de que GitHub Advanced Security esté habilitado.

Los jobs tienen `permissions` mínimos, `timeout`, cancelación de ejecuciones obsoletas y acciones fijadas a SHA. Dependabot revisa semanalmente GitHub Actions, Maven y el árbol npm de la web.

La aplicación móvil se mudó a [proyecto4to/techstore-mobile](https://github.com/proyecto4to/techstore-mobile) y aplica esta misma política desde su propio workflow, con los mismos gates compartidos. Su baseline y sus particularidades están documentadas allá.

## Política de dependencias

`npm audit --omit=dev --audit-level=critical` bloquea vulnerabilidades críticas en dependencias de producción. Las severidades inferiores no se ocultan: quedan visibles en el log y deben corregirse con una actualización compatible, nunca mediante un downgrade forzado de Expo.

Baseline al cerrar Fase 14:

| Árbol | Críticas | Altas | Moderadas |
| --- | ---: | ---: | ---: |
| Frontend producción | 0 | 1 | 0 |

Cada stack genera un SBOM CycloneDX JSON. La revisión automática rechaza licencias de distribución fuerte no aprobadas (AGPL/GPL-3, SSPL, BUSL/Commons Clause); no sustituye una revisión legal previa a publicación comercial.

## Secretos y artefactos

`scripts/ci-security-check.mjs` falla ante:

- nuevos `.p12`, `.pfx`, `.pem`, `.key`, `.jks`, `.keystore`, credenciales Google/Firebase/Apple o `.env` reales;
- marcadores reconocibles de claves privadas y tokens comunes;
- uso activo de `8080` o `8181` fuera de documentación;
- credenciales inyectadas en el workflow;
- archivos sensibles dentro del JAR, bundle web o export Android.

Existe una única excepción heredada y explícita: `techstore-backend/src/main/resources/sifen/techstore-fe-test.p12`. El gate la reporta como warning para impedir excepciones nuevas, pero no puede eliminarla ni rotarla sin la autoridad SIFEN. Sigue siendo obligatorio rotar, externalizar y luego retirar tanto el archivo como su excepción.

## Evidencia y retención

Los SBOM se conservan como artifacts de GitHub Actions durante 14 días. El CI no compila APK/AAB ni recibe credenciales de firma; esos artefactos se generan con EAS y su secret store en Fase 15. Para distribución, conservar además URL/ID de build, hash descargado, perfil EAS, versión y resultado del escaneo del binario.

## Reproducción local

```powershell
node scripts/ci-security-check.mjs

cd techstore-backend
..\tools\apache-maven-3.9.9\bin\mvn.cmd clean verify

cd ..\techstore-frontend
npm ci
npm run check
npm audit --omit=dev --audit-level=critical
```

Los puertos `8080` y `8181` permanecen reservados para GeneXus/Tomcat. Metro usa `8081` y la API TechStore usa `8090`.
