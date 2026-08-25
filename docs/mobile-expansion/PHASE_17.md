# FASE 17 — RELEASE

ESTADO: COMPLETED

## Distribución elegida

El alcance vigente es instalación privada por APK en los Android que autorice el usuario. Google Play, Play App Signing, App Links públicos y Firebase production quedan postergados y no son gates de esta entrega.

## Identidad y seguridad

- Flavor: `private`.
- Application ID: `py.com.techstore.app.private`.
- Nombre: TechStore.
- Versión final: `1.0.1+2`.
- API incluida: `http://192.168.100.8:8090`, aceptada únicamente por el flavor privado y solo para IPv4 RFC1918.
- Production continúa exigiendo HTTPS y no fue debilitado.
- `allowBackup=false`, app release no-debuggable, Dart ofuscado y símbolos separados.
- Firma privada externa al repositorio; Gradle falla cerrado si falta `android/key.properties`.

## Firma

- Alias: `techstore-private`.
- Certificado SHA-256: `c4a098390fc9f299a68dd51b79741ad931485ea012828e613afc7f801aabf0a2`.
- Vencimiento: 2051-08-17.
- Keystore: `%LOCALAPPDATA%\TechStore\mobile-signing\techstore-private.p12`.
- Copia de credenciales y certificado público: mismo directorio `mobile-signing`.
- ACL restringida al usuario actual y SYSTEM; las contraseñas no se imprimen.

Es obligatorio respaldar de forma segura todo `%LOCALAPPDATA%\TechStore\mobile-signing`. Sin esa clave no se podrá actualizar una instalación existente con el mismo package.

## Pipeline

- `scripts/provision-mobile-private-signing.ps1`: crea y protege la firma privada.
- `scripts/build-mobile-private.ps1`: valida URL, analiza, prueba, compila, ofusca, escanea, firma, calcula hashes y genera metadatos.
- `scripts/check-mobile-private-readiness.ps1`: valida backend, firma, APK, dispositivo, evidencia nativa y actualización directa.

El preflight privado aprobó `7/7` controles con `PRIVATE_RELEASE_READY=True`.

## Criterios

- [x] Application ID, flavor, icono, splash y versionado configurados.
- [x] Firma privada real y recuperable fuera de Git.
- [x] APK escaneado, firmado y con SHA-256 registrado.
- [x] Instalación y actualización física comprobadas.
- [x] Login, backend, offline, reconexión, cámara y biometría comprobados.
- [x] Sin secretos de firma dentro del APK o el repositorio.
