# Fase 2 — API y autenticación segura

**Estado:** completada  
**Fecha de cierre:** 2026-08-10  
**Alcance:** contrato OpenAPI, cliente HTTP, autenticación móvil, SecureStore, rotación de refresh y logout revocable.

## Resultado

TechStore Mobile puede iniciar y crear cuentas contra la API real, restaurar una sesión, renovar el access token de forma coordinada y cerrar sesión. El backend mantiene sesiones refresh revocables y rotativas sin guardar JWT utilizables en la base.

## Entregado

- Contrato OpenAPI 3.1 para login, registro, refresh, logout y perfil.
- Generación reproducible de tipos con `npm run api:generate`.
- Axios con timeout de 15 segundos, Bearer en memoria, errores normalizados y un solo refresh para 401 concurrentes.
- Validación runtime de respuestas y formularios con Zod; React Hook Form en login y registro.
- Refresh token en SecureStore nativo; web usa memoria efímera. No se usan AsyncStorage, localStorage ni sessionStorage para credenciales.
- Bootstrap de sesión antes de ocultar el splash, perfil en Cuenta y limpieza local garantizada al salir.
- Migración V35 con hash SHA-256, `jti`, rotación, revocación, detección de reutilización, índices y RLS.
- Endpoint `POST /api/v1/auth/logout`; cambio de contraseña revoca las sesiones activas.
- Pruebas unitarias móviles del vault y refresh concurrente, y prueba backend HTTP del ciclo completo.

## Límites declarados

- Recuperación de contraseña no está disponible porque falta su endpoint backend.
- El tenant slug se valida, pero el backend todavía resuelve login/registro anónimos al tenant predeterminado; no se confía en una cabecera libre enviada por el cliente.
- El catálogo, carrito, checkout y pedidos continúan como demostración hasta las fases siguientes.
- En web el refresh no sobrevive una recarga deliberadamente; persistir una credencial en storage web ampliaría el riesgo XSS.

## Evidencia de validación

| Control | Resultado |
| --- | --- |
| `mvn verify` | 272 pruebas unitarias y 31 de integración; 0 fallos/errores. |
| Migraciones | V1–V35 aplicadas desde cero en PostgreSQL 15 real. |
| `npm run api:generate` | Contrato generado sin errores. |
| `npm run check` | TypeScript y lint limpios; 4 suites/8 tests aprobados. |
| `npx --no-install expo-doctor` | 20/20 comprobaciones aprobadas. |
| `npm run build:web` | Export estático correcto, 19 rutas y tres imágenes de catálogo incluidas. |

## Dependencias

`npm audit` informa 23 nodos transitivos (`15 high`, `8 moderate`, `0 critical`) vinculados a Expo/Metro y tooling iOS. No hay parche compatible para Expo SDK 57; `npm audit fix --force` propone downgrades incompatibles y no debe ejecutarse. Se aceptan temporalmente assets confiables, timeouts de CI y seguimiento de actualizaciones upstream.

## Criterios de aceptación

- [x] Contrato versionado y tipos generables.
- [x] URL por ambiente validada; producción exige HTTPS público.
- [x] Access token fuera de persistencia.
- [x] Refresh token protegido y rotativo.
- [x] Refresh concurrente single-flight y retry único.
- [x] Logout revocable y limpieza local garantizada.
- [x] RLS y pruebas de integración en verde.
- [x] Limitaciones de tenant y recuperación documentadas sin simular funcionalidad.

La Fase 3 puede consumir el catálogo público con DTO móvil seguro, paginación, búsqueda y estados reales de carga/error.
