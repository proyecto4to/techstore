# RELEASE ANDROID

ESTADO: RELEASE PRIVADO COMPLETED; publicación pública postergada.

## APK privada vigente

- Archivo: `artifacts/mobile/private/TechStore-Private-1.0.2+3.apk`.
- Application ID: `py.com.techstore.app.private`.
- SHA-256: `ff30bd3958ca11f821718a2c6e4f3793b1a4ddc6e549354e716e3386e69641b2`.
- Certificado SHA-256: `c4a098390fc9f299a68dd51b79741ad931485ea012828e613afc7f801aabf0a2`.
- API LAN: `http://192.168.100.8:8090`.
- Instalación y actualización `1.0.0+1` → `1.0.1+2`: verificadas en Android físico.
- Actualización `1.0.2+3`: artefacto firmado con el mismo certificado y listo; instalación física pendiente porque el dispositivo no estuvo visible por ADB al finalizar el build.
- Preflight: `scripts/check-mobile-private-readiness.ps1`, `7/7 PASS`.

Para recompilar se usa `scripts/build-mobile-private.ps1`. HTTP solo se acepta hacia IPv4 privada RFC1918; el flavor production continúa exigiendo HTTPS.

La firma y sus credenciales están fuera del repositorio en `%LOCALAPPDATA%\TechStore\mobile-signing`. Respaldar el directorio completo: sin el mismo keystore Android rechazará futuras actualizaciones sobre la app instalada.

## Publicación pública futura

### Identidad

- Application ID: `py.com.techstore.app`.
- Nombre: TechStore.
- Versión base: `1.0.0+1`; cada publicación debe incrementar `versionCode` de forma estricta.
- Ícono/splash: `techstore-mobile/assets/branding/` y recursos Android generados.

El ícono fue generado con la herramienta integrada de ImageGen usando este concepto final: monograma T geométrico integrado en bolsa/circuito, navy + cyan/teal + acento amber, sin texto, composición centrada y margen seguro adaptativo.

### Firma

Crear una upload key de organización fuera del repositorio y copiar `techstore-mobile/android/key.properties.example` a `android/key.properties`. Este último y `*.jks`/`*.keystore` están ignorados.

Production falla cerrado cuando falta ese archivo. `TECHSTORE_ALLOW_LOCAL_RELEASE_SIGNING=true` existe exclusivamente para builds de auditoría con certificado debug y nunca debe usarse para entregar/publicar.

Conservar en un gestor de secretos:

- upload keystore;
- alias;
- passwords;
- certificado público y huellas SHA-256;
- procedimiento y custodios de recuperación.

### Compilación final

```powershell
.\scripts\build-mobile-release.ps1 `
  -ApiBaseUrl https://api.techstore.tld `
  -VersionName 1.0.0 `
  -VersionCode 1
```

El script rechaza HTTP, localhost, emulador, placeholders y ausencia de firma. Produce en `artifacts/mobile/`:

- APK para smoke test controlado;
- AAB para Google Play;
- símbolos de ofuscación;
- SHA-256 y `release-metadata.json`;
- resultado del escaneo de seguridad.

### Google Play y actualización

Producción usa Google Play App Bundle + Play App Signing. La app consulta `GET /api/v1/app/version`, ejecuta update flexible para versiones recomendadas e inmediato para incompatibles, y abre la ficha HTTPS de Play como fallback. No solicita `REQUEST_INSTALL_PACKAGES` ni instala APK lateralmente.

Antes de elevar `MOBILE_MIN_VERSION_CODE`:

1. Publicar la versión objetivo en una pista.
2. Completar/estabilizar rollout.
3. Confirmar disponibilidad desde un dispositivo instalado por Play.
4. Recién entonces elevar el mínimo en backend.

### Checklist de publicación

Ejecutar primero `scripts/check-mobile-release-readiness.ps1`. El preflight no imprime contraseñas y falla mientras no pueda validar URL/DNS/TLS production, keystore/alias, package Firebase, Android físico, commit limpio y las evidencias manuales de Play/App Links/FCM/smoke test.

- [ ] Dominio/API HTTPS real y certificado válido.
- [ ] App Link verificado con `assetlinks.json`.
- [ ] Firebase Android production y entrega push probada.
- [ ] Upload key y Play App Signing configurados.
- [ ] Ficha, privacidad, Data Safety, capturas y clasificación Play completas.
- [ ] `flutter analyze`, `flutter test`, backend/Web/E2E verdes.
- [ ] APK/AAB escaneados, firmas y hashes verificados.
- [ ] Instalación, login, sync, offline, nativo y v1→v2 probados físicamente.
- [ ] Símbolos y metadata archivados con acceso restringido.
