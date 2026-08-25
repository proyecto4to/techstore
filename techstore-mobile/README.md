# TechStore Mobile

Cliente Android Flutter del mismo backend y base de datos utilizados por TechStore Web.

## Requisitos

- Flutter 3.47.1 / Dart 3.13.1
- Android SDK y JDK 17 o superior

## Desarrollo

```powershell
flutter pub get
flutter run --flavor development -t lib/main_development.dart
```

El emulador usa `http://10.0.2.2:8090` por defecto. En un dispositivo físico se debe indicar una URL alcanzable:

```powershell
flutter run --flavor development -t lib/main_development.dart --dart-define=API_BASE_URL=http://192.168.1.10:8090
```

Staging y producción exigen HTTPS y una URL explícita. Para probar Staging:

```powershell
flutter build apk --flavor staging -t lib/main_staging.dart --dart-define=API_BASE_URL=https://staging-api.techstore.tld
```

Las URL no son secretos. Tokens, contraseñas y claves nunca deben pasarse mediante `dart-define` ni incluirse en el APK.
Production se genera únicamente con `scripts/build-mobile-release.ps1`, que valida URL, firma, pruebas, hashes y artefactos.

## Calidad

```powershell
dart format --output=none --set-exit-if-changed lib test
flutter analyze --fatal-infos
flutter test
flutter build apk --debug --flavor development -t lib/main_development.dart
```

## Release Android

1. Crear el upload key fuera del repositorio.
2. Copiar `android/key.properties.example` como `android/key.properties` y completar sus cuatro valores localmente.
3. Ejecutar desde la raíz:

```powershell
.\scripts\build-mobile-release.ps1 `
  -ApiBaseUrl https://api.techstore.tld `
  -VersionName 1.0.0 `
  -VersionCode 1
```

El script rechaza HTTP, hosts locales/placeholders y releases sin firma. Genera APK, AAB, símbolos de ofuscación, hashes SHA-256 y `artifacts/mobile/release/release-metadata.json`. Google Play debe usar el AAB y Play App Signing; el APK es para smoke test controlado.
