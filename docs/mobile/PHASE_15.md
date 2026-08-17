# Fase 15 — Builds y preparación de distribución

**Estado:** implementación y builds terminados; aceptación física pendiente  
**Fecha:** 2026-08-13

## Resultado técnico

- Development build Android: `FINISHED`.
- APK interno autónomo generado, descargado, firmado y verificado: `FINISHED`.
- AAB técnico generado, descargado y verificado: `FINISHED`.
- Configuración iOS, bundle identifier, versionado y guardas de producción preparados.
- Credenciales Apple y API pública HTTPS permanecen fuera del repositorio.

Los perfiles y validadores están en `techstore-mobile/eas.json`, `app.config.ts` y `scripts/validate-build-config.cjs`. `.easignore` limita el contexto remoto y excluye backend, web, documentación, pruebas, temporales, certificados y secretos.

| Salida | Build ID | Versión interna | Estado |
| --- | --- | --- | --- |
| APK preview correctivo | `a759bc63-77c9-4e19-a66b-f06c49971b81` | 11 | `FINISHED` |
| AAB store-preview | `764a7083-ffd9-49b2-a39b-ec6f167e337e` | 10 | `FINISHED` |
| APK development | `c4fd27ea-216a-4e5c-bcb0-4e31a36c7729` | 10 | `FINISHED` |

APK y AAB contienen ARM64 y aprobaron el escaneo de secretos. El certificado raíz público de Expo se admite sólo por sus rutas exactas. La API incorporada en los builds internos es `http://192.168.16.125:8090/api/v1`; no se usan `8080` ni `8181`.

El APK v11 reemplaza al candidato v9 después de que MIUI rechazara su instalación. `apksig` confirmó que v8, v9 y v11 tienen firma v2 válida y el mismo certificado; v11 eleva el `versionCode` por encima del development build v10 para evitar un rechazo por downgrade.

## Validación automatizada

| Producto | Resultado |
| --- | --- |
| Backend unitario | 315/315 |
| Backend integración | 37/37; 42 migraciones Flyway |
| Web | lint, 6 archivos/12 tests y build aprobados |
| Mobile | typecheck, lint, 22 suites/60 tests aprobados |
| Expo | Doctor 20/20, config, export y E2E estático aprobados |
| Builds | APK/AAB íntegros; firma APK y escáner aprobados |

## Archivos principales

- `.easignore`
- `techstore-mobile/app.config.ts`
- `techstore-mobile/eas.json`
- `techstore-mobile/scripts/validate-build-config.cjs`
- `techstore-mobile/tests/appConfig.test.ts`
- `scripts/ci-security-check.mjs`
- `docs/mobile/PREVIEW_APK.md`
- `docs/mobile/BUILD_ANDROID.md`
- `docs/mobile/BUILD_IOS.md`
- `docs/mobile/PHASE_15.md`

## Nueva migración

Ninguna. La Fase 15 no modifica el modelo de datos.

## Pendientes

- Ejecutar el checklist manual completo en el Xiaomi con backend activo y misma Wi-Fi. Este checkpoint impide declarar aceptada la Fase 15 antes de esa prueba.
- Recompilar si cambia la IP LAN de la PC.
- Para Google Play real, usar API HTTPS pública, perfil `production` y completar Play Console.
- Para iOS, aportar membresía/credenciales Apple autorizadas y efectuar TestFlight/App Store.
