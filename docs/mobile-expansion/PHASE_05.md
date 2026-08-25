# FASE 05 — Proyecto Flutter

ESTADO: COMPLETED

## Resultado

- Proyecto Flutter 3.47.1 / Dart 3.13.1 creado en `techstore-mobile` con plataforma Android.
- Application ID base definitivo `py.com.techstore.app`.
- Estructura feature-first alineada con el mapa funcional y separación `app`, `core` y `features`.
- Riverpod, Dio, go_router, Drift, secure storage, conectividad, STOMP y generación de modelos preparados con lockfile reproducible.
- Flavors `development`, `staging` y `production`, con entrypoint, nombre y package ID independientes.
- Staging y producción rechazan URLs no HTTPS o ausentes al iniciar.
- Lints estrictos, formato, tests y compilación Android incorporados al CI.

## Criterios de aceptación

- [x] Flutter y Dart configurados y verificados.
- [x] Proyecto Android funcional, sin WebView.
- [x] Arquitectura basada en features.
- [x] Dependencias fijadas en `pubspec.lock`.
- [x] Lint y formato sin observaciones.
- [x] Development, Staging y Production diferenciados.
- [x] Los tres variants Android compilan.
- [x] CI valida el cliente mobile.
- [x] Sin pendientes críticos de fase.

## Pruebas

- `dart format --output=none --set-exit-if-changed lib test`: OK.
- `flutter analyze --fatal-infos`: sin issues.
- `flutter test`: 3/3 aprobadas.
- APK debug Development: `py.com.techstore.app.dev`, compilado.
- APK debug Staging: `py.com.techstore.app.staging`, compilado.
- APK debug Production: `py.com.techstore.app`, compilado.
- Manifest inspeccionado: actividad `py.com.techstore.app.MainActivity` y permiso de Internet correctos.
- `flutter doctor -v`: toolchain Android correcto; Visual Studio ausente es irrelevante para un proyecto solo Android.

## Decisiones de compatibilidad

`flutter_secure_storage` queda fijado en 10.0.0 porque la versión 11 exige Android SDK 37, todavía no disponible como target estable en el toolchain Android 36 utilizado por Flutter 3.47.1. Mantiene almacenamiento respaldado por Android Keystore.
