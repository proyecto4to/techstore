# SEGURIDAD

ESTADO: AUDITADO — release privado firmado y validado; publicación pública postergada.

- Backend y PostgreSQL son la autoridad; Flutter no contiene reglas críticas ni credenciales.
- Access token en memoria y refresh token en almacenamiento seguro Android.
- Rotación de refresh, revocación en logout y recuperación con token de un solo uso.
- Autorización por rol en rutas Mobile y nuevamente en cada endpoint backend.
- STOMP exige JWT y restringe suscripciones por tenant/usuario.
- Logs de red redactan secretos y no registran cuerpos sensibles.
- URLs por flavor no son secretos; claves y contraseñas nunca se compilan en el APK.
- RLS y contexto de tenant aíslan datos en PostgreSQL.
- Los comprobantes de compra se guardan con nombre UUID interno, se validan por firma y solo se descargan desde un endpoint autenticado; el handler público sirve exclusivamente imágenes de productos.
- FCM usa Application Default Credentials en backend y valores públicos de configuración por flavor; no se incluye una cuenta de servicio en el repositorio ni en el APK.

- Manifest production: backup y cleartext deshabilitados; desarrollo habilita HTTP solo para emulador.
- Flavor privado: cleartext limitado a una API IPv4 RFC1918 validada por el build; no modifica staging/production.
- Permisos transitivos de audio/video eliminados; no existe permiso para instalar APK.
- SBOM y licencias: backend 211 componentes y Web 308, sin licencias sin resolver; npm audit 0 vulnerabilidades.
- Scanner: repositorio y APK sin secretos. Permanece advertencia conocida del certificado SIFEN demostrativo, que debe externalizarse/rotarse antes de homologación.
- Password reset debe migrar del custom scheme a Android App Links verificados cuando exista el dominio productivo definitivo.
- Production falla cerrado sin upload keystore externo; el build de auditoría con certificado debug requiere una variable explícita y no es publicable.
- El APK privado usa keystore externo, certificado no-debug, ofuscación, símbolos separados, hash SHA-256 y preflight reproducible; el smoke test físico aprobó.
- Dominio HTTPS, Play/Firebase y App Links solo siguen pendientes para una publicación pública futura.
