# Fase 12 — Biometría y hardening OWASP MASVS

**Estado:** completada y validada  
**Fecha de cierre:** 2026-08-13  
**Alcance:** bloqueo local opcional, aislamiento de datos entre sesiones y endurecimiento de storage/configuración móvil.

## Resultado

TechStore permite que cada sesión guardada active protección biométrica fuerte desde la pantalla Seguridad. La app oculta completamente su árbol autenticado mientras resuelve la preferencia, solicita biometría al abrir y vuelve a bloquearse después de 30 segundos en segundo plano.

La función es deliberadamente local: no cambia JWT, refresh, roles ni reglas del backend. Cancelar no desbloquea; un sensor no disponible muestra un mensaje seguro y permite cerrar sesión.

## Hardening aplicado

- Refresh token migrado de una constante de accesibilidad deprecada a `WHEN_UNLOCKED_THIS_DEVICE_ONLY`.
- Preferencia biométrica cifrada en SecureStore y vinculada solo al ID de la cuenta activa.
- Android limitado a biometría clase 3 mediante `biometricsSecurityLevel: "strong"`.
- Fallback al PIN deshabilitado para no confundir biometría con autenticación backend.
- Cache privado de TanStack Query, borrador de checkout, badge y preferencia biométrica limpiados al cambiar/cerrar/vencer sesión.
- Config plugin de SecureStore explícito para Android Auto Backup y descripción de Face ID.
- Declaración iOS de criptografía no exenta configurada para App Store Connect.
- Producción HTTPS, permisos mínimos, deep-link allowlist y tokens en memoria verificados y documentados.

## Evidencia de validación

| Control | Resultado |
| --- | --- |
| Backend unitario | 315/315 pruebas aprobadas. |
| Migraciones PostgreSQL 15 | `MigracionesIT` 15/15; 42 migraciones. |
| Frontend web | ESLint, 12/12 pruebas y build aprobados. |
| TypeScript móvil | Sin errores. |
| ESLint móvil | Sin errores ni warnings de React Compiler. |
| Jest móvil | 21 suites/51 pruebas; 4 casos nuevos de biometría/storage. |
| Expo Doctor | 20/20 comprobaciones. |
| Config Expo | Plugins, permisos, package/bundle ID y cifrado iOS resueltos correctamente. |
| Export Android | Bundle Hermes Android generado correctamente. |
| Higiene | `git diff --check` sin errores; sin puertos reservados en configuración activa. |

## Archivos principales de esta fase

- `techstore-mobile/src/features/security/services/biometricLockService.ts`
- `techstore-mobile/src/features/security/components/BiometricLockGate.tsx`
- `techstore-mobile/src/features/security/components/SessionPrivacyManager.tsx`
- `techstore-mobile/src/features/security/screens/SecurityScreen.tsx`
- `techstore-mobile/src/app/security/index.tsx`
- `techstore-mobile/src/providers/AppProviders.tsx`
- `techstore-mobile/src/api/tokenVault.ts`
- `techstore-mobile/app.config.ts`
- `techstore-mobile/tests/biometricLockService.test.ts`
- `techstore-mobile/tests/tokenVault.test.ts`
- `docs/mobile/SECURITY.md`
- `docs/security/SECURITY_HARDENING.md`

## Nueva migración

Ninguna. La Fase 12 no modifica el modelo de datos.

## Pendientes

- Validación manual de huella en el Xiaomi Redmi Note 8 Pro.
- Face ID debe probarse en un development/preview build físico; Expo Go no lo soporta por completo.
- SAST, SCA, secret scanning y controles de artefacto se integran en Fase 14.
- La rotación del certificado SIFEN histórico y las credenciales productivas requieren autoridad externa y siguen pendientes.

## Criterios de aceptación

- [x] Biometría opcional y fuerte, sin reemplazar autenticación backend.
- [x] Sesión oculta hasta resolver/desbloquear el gate local.
- [x] Cancelación y errores no abren la app.
- [x] Limpieza de cache y estado privado entre sesiones.
- [x] Storage, red, permisos, plataforma y privacidad mapeados a MASVS.
- [x] Regresión backend, web y móvil en verde.
