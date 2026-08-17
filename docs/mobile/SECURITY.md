# Seguridad de TechStore Mobile

**Última revisión:** 2026-08-13  
**Base:** Expo SDK 57, React Native 0.86, OWASP MASVS como guía de defensa en profundidad.

## Modelo de confianza

La aplicación móvil no es una fuente de verdad. Spring Boot vuelve a autenticar y autorizar cada operación, deriva usuario y tenant del contexto seguro y calcula precios, stock, impuestos, pagos y estados. Ocultar una acción en la interfaz nunca concede ni revoca permisos.

## Sesión y almacenamiento

- Access token únicamente en memoria del proceso.
- Refresh token únicamente en Expo SecureStore nativo; la vista web de desarrollo lo mantiene solo en memoria.
- SecureStore usa `WHEN_UNLOCKED_THIS_DEVICE_ONLY`: el refresh solo es accesible mientras el dispositivo está desbloqueado y no se migra a otro equipo.
- Interceptor single-flight: una renovación concurrente, un retry por request y cierre local si el refresh falla.
- Logout intenta revocar la sesión en backend y elimina tokens localmente incluso si la red falla.
- Al cerrar o vencer la sesión se vacía el cache de TanStack Query, el checkout persistido, el badge del sistema y la preferencia biométrica de esa sesión.
- AsyncStorage queda limitado al carrito y borradores no sensibles del checkout; nunca contiene contraseña, access token, refresh token ni secretos.

## Bloqueo biométrico

La opción se activa desde `Mi cuenta → Seguridad y biometría` y requiere una comprobación satisfactoria antes de guardarse.

- Android exige `biometricsSecurityLevel: "strong"` (clase 3).
- iOS usa Touch ID o Face ID administrado por el sistema.
- El fallback a PIN dentro del prompt se deshabilita; cancelar conserva la sesión bloqueada.
- La sesión se bloquea al abrir la app y al regresar después de 30 segundos en segundo plano.
- Siempre existe una salida de “Cerrar sesión” para no atrapar al usuario si el sensor queda indisponible.
- TechStore guarda solamente el ID de la cuenta que activó el bloqueo. No recibe ni almacena huellas, rostro o iris.
- La biometría protege acceso local; no sustituye login, refresh, revocación ni autorización del backend.

Face ID no puede validarse completamente en Expo Go y requiere un development/preview build físico. Referencias exactas: [LocalAuthentication SDK 57](https://docs.expo.dev/versions/v57.0.0/sdk/local-authentication/) y [SecureStore SDK 57](https://docs.expo.dev/versions/v57.0.0/sdk/securestore/).

## Red y configuración

- Producción rechaza API por HTTP, `localhost`, `127.0.0.1` y `10.0.2.2`.
- Desarrollo/preview pueden usar una URL LAN explícita; no se desactiva la validación TLS.
- La API activa de TechStore usa `8090`; `8080` y `8181` están reservados para GeneXus/Tomcat.
- `EXPO_PUBLIC_*` se trata como información pública y nunca contiene secretos.
- Axios aplica timeout, errores normalizados y no registra tokens ni cuerpos sensibles.

## Plataforma y privacidad

- Permisos de cámara, micrófono, contactos, ubicación y almacenamiento amplio están bloqueados mientras no exista una función justificada.
- Notificaciones se solicitan con contexto desde Cuenta, no al abrir por primera vez.
- Los deep links usan allowlist de recurso e ID numérico; el backend vuelve a validar ownership.
- El config plugin declara el texto de Face ID y excluye SecureStore del Android Auto Backup no descifrable.
- `usesNonExemptEncryption: false` documenta que la app no implementa criptografía propia no exenta; usa la protección del sistema.

## Cobertura OWASP MASVS

| Área | Controles implementados | Pendientes externos o de release |
| --- | --- | --- |
| STORAGE | SecureStore, token en memoria, cache privado limpiado, AsyncStorage no sensible. | Escaneo del APK/AAB final. |
| CRYPTO | TLS obligatorio en producción, Keystore/Keychain, sin criptografía casera. | Certificados y TLS del dominio productivo. |
| AUTH | Refresh rotado/revocable, rate limit backend, biometría auxiliar fuerte. | Pruebas físicas Android/iOS y recuperación pública completa. |
| NETWORK | URL centralizada, HTTPS production, timeout y sin token en URL. | Pinning solo si el modelo de riesgo y la rotación operativa lo justifican. |
| PLATFORM | Permisos mínimos, deep links allowlist, prompt biométrico nativo. | Universal/App Links al disponer de dominio oficial. |
| CODE | TypeScript estricto, Zod, lint, tests y errores sanitizados. | SAST/SCA/secret scan en CI de Fase 14. |
| RESILIENCE | Backend como fuente de verdad, idempotencia, RLS y outbox. | Attestation opcional según fraude/riesgo comercial. |
| PRIVACY | Minimización, sin biometría/contraseña local, cache por sesión. | Política legal y retención aprobadas por responsable del negocio. |

## Prueba manual mínima

1. Iniciar sesión en un dispositivo con huella segura configurada.
2. Abrir `Mi cuenta → Seguridad y biometría` y activar la protección.
3. Cerrar y abrir la app; confirmar que el contenido no aparece antes de autenticar.
4. Cancelar el prompt; confirmar que continúa bloqueada.
5. Desbloquear correctamente.
6. Enviar la app a segundo plano por más de 30 segundos y volver.
7. Cerrar sesión; iniciar de nuevo y confirmar que la preferencia anterior no se aplica a otra sesión/cuenta.
8. Repetir en un equipo sin biometría o con biometría débil; la opción debe quedar inactiva con explicación.

## Respuesta ante incidentes

No copiar tokens, contraseñas ni payloads privados en reportes. Registrar pantalla, acción, hora aproximada, conectividad, versión de app y mensaje visible. Revocar sesiones y rotar credenciales desde backend cuando exista sospecha de compromiso.
