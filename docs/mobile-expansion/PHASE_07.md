# FASE 07 — Autenticación

ESTADO: COMPLETED

## Resultado

- Login, logout y restauración de sesión integrados con la API real.
- Access token mantenido solo en memoria y refresh token protegido mediante Android Keystore.
- Renovación anticipada, rotación obligatoria y exclusión mutua para múltiples respuestas `401` concurrentes.
- Guards de navegación para sesión y rol administrador.
- Modelo de roles y permisos `Admin`, `Cajero` y `Cliente` alineado con backend.
- Recuperación de contraseña completa mediante deep link `techstore://app/password/reset`.
- Backend con tokens de recuperación aleatorios, hash SHA-256, vencimiento, consumo único y revocación de sesiones.
- Migración Flyway V44 con aislamiento por tenant y eliminación en cascada.
- Lanzador local corregido para seleccionar automáticamente Java 21 o superior.

## Criterios de aceptación

- [x] Login y logout contra backend y PostgreSQL reales.
- [x] Access token en memoria y refresh rotativo single-flight.
- [x] Restauración, expiración y cierre de sesión consistentes.
- [x] Roles, permisos y guards aplicados.
- [x] Solo el refresh token persiste en secure storage.
- [x] Recuperación de contraseña completa y no enumerable.
- [x] OpenAPI actualizado y validado.
- [x] Aplicación Android compila sin issues.
- [x] Sin pendientes críticos de fase.

## Pruebas

- Backend: 324 unitarias y 43 de integración aprobadas (`mvn verify`).
- Recuperación: correo conocido/desconocido indistinguible, token de un uso y login con nueva contraseña.
- Mobile: `flutter analyze` sin issues y 9/9 tests aprobados.
- Concurrencia: tres renovaciones simultáneas producen una sola llamada refresh.
- E2E real: registro efímero → login → refresh rotativo → logout; datos efímeros eliminados en transacción.
- Android: `app-development-debug.apk` compilado.
- OpenAPI 3.1: 100 rutas y 122 operaciones validadas.
- Lanzador: Java 22 seleccionado; PostgreSQL, backend y frontend operativos.
- Escaneo de seguridad: OK con la excepción SIFEN heredada ya documentada.

## Decisiones de seguridad

- Las contraseñas y tokens nunca se imprimen en el comprobador E2E.
- La respuesta de recuperación no revela si una cuenta existe.
- La biometría protege el acceso local y se implementará en la Fase 09; no reemplaza la autoridad del backend.
