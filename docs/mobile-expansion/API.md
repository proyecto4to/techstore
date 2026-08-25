# API compartida Web + Mobile

## Contrato canónico

- Versión: `/api/v1`.
- Especificación: `docs/api/techstore-v1.openapi.json`.
- Formato: OpenAPI 3.1, 102 rutas y 125 operaciones identificadas de forma única.
- Base URL: configurable por ambiente; el contrato usa `/` para no fijar hosts de desarrollo.
- Fuente: aplicación Spring Boot en ejecución, exportada con `scripts/export-openapi.ps1`.
- Validación reproducible: `node scripts/validate-openapi.mjs`.

`docs/api/mobile-auth.openapi.yaml` se conserva como contrato histórico del cliente Expo, discontinuado por ADR-011. No es la fuente de verdad.

## Autenticación y tenant

Las operaciones protegidas heredan `bearerAuth` y reciben un JWT mediante `Authorization: Bearer <token>`. Registro, login, refresh, logout, catálogo público, política de versión Mobile y validación pública del carrito declaran explícitamente `security: []`. El tenant se resuelve en backend desde el host/contexto autenticado; el cliente no puede elegir libremente otro tenant.

`GET /api/v1/app/version?platform=android&versionCode={n}` publica la política de compatibilidad Mobile y una URL HTTPS de Google Play; no entrega binarios.

El flujo de sesión utiliza access token de vida corta y refresh token rotativo. Los roles vigentes son `PUBLIC`, `CLIENTE`, `CAJERO` y `ADMIN`; las autorizaciones concretas siguen centralizadas en Spring Security y servicios de dominio.

La recuperación usa `POST /api/v1/auth/password/forgot` (respuesta genérica `202`, exista o no el correo) y `POST /api/v1/auth/password/reset` (`204`). El enlace contiene un token aleatorio de un solo uso; en base solo se persiste su hash SHA-256, tiene vencimiento configurable y al consumirse revoca todas las sesiones refresh del usuario.

## Convenciones

- JSON UTF-8 para cuerpos ordinarios.
- `multipart/form-data` para imágenes y documentos aceptados por cada operación.
- Fechas y horas en ISO-8601.
- Paginación mediante parámetros `page`, `size` y orden cuando el endpoint retorna una página.
- Errores mediante el manejador global con estado HTTP coherente y cuerpo estructurado; nunca se usa un `200` para representar un error de negocio.
- Descargas responden con `Content-Type` y `Content-Disposition` apropiados.
- `POST /api/v1/compras/{id}/comprobante/archivo` adjunta JPG/PNG/PDF y `GET` en la misma ruta lo descarga con autorización `Admin/Cajero`.
- `GET /api/v1/facturas`, `GET /api/v1/facturas/{id}` y `GET /api/v1/facturas/{id}/pdf` permiten a `Admin/Cajero` consultar cualquier factura y restringen al cliente autenticado a las facturas de sus propios pedidos.
- La validación de reglas, stock, permisos, MIME real y límites reside en backend.

## Compatibilidad

La Web actual y Flutter consumen las mismas rutas y reglas. Los cambios incompatibles requieren una nueva versión (`/api/v2`) o un periodo de transición; agregar campos opcionales es compatible. Los eventos de tiempo real no transportan una segunda representación autoritativa: notifican el cambio y los clientes revalidan por REST.

## Actualización del contrato

1. Iniciar el backend con la configuración del ambiente deseado.
2. Ejecutar `powershell -File scripts/export-openapi.ps1` desde la raíz.
3. Ejecutar `node scripts/validate-openapi.mjs`.
4. Revisar el diff del JSON y actualizar clientes/tests cuando corresponda.

El exportador normaliza el servidor y la seguridad de las operaciones públicas para producir un artefacto portable y determinista.
