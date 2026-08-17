# TechStore — plan de hardening de seguridad

Fecha: 2026-08-13  
Alcance: backend, frontend web, app móvil, base de datos, CI/CD y operación.

## 1. Principios obligatorios

- Spring Boot y PostgreSQL son la fuente de verdad para identidad, tenant, precios, IVA, stock, pedidos, pagos y fiscalidad.
- Ningún `usuarioId`, `tenantId`, rol, precio, descuento, total o estado enviado por un cliente se considera confiable.
- Un fallo de SMTP, push o proveedor externo no debe revertir ni duplicar una operación comercial confirmada.
- Los secretos no pertenecen al repositorio, JAR, imagen Docker, frontend web, APK, AAB ni IPA.
- El acceso se concede por identidad autenticada, tenant y ownership/rol; conocer un ID nunca basta.
- Los logs no deben contener contraseñas, access/refresh tokens, claves privadas, payloads fiscales sensibles ni PII innecesaria.
- Las migraciones aplicadas son inmutables. V35 incorpora sesiones refresh seguras y V36 favoritos con RLS; todo cambio adicional se agrega desde V37 o la siguiente versión libre.

## 2. Incidente prioritario: certificado SIFEN versionado

### Evidencia

`techstore-backend/src/main/resources/sifen/techstore-fe-test.p12` está trackeado, aparece en el historial Git y es alcanzado por el empaquetado Maven. El Dockerfile copia el JAR resultante a la imagen. La `.dockerignore` no evita que un recurso ya empaquetado termine en el artefacto.

No se inspeccionó ni se documenta el contenido, alias o password del certificado.

### Tratamiento

1. Clasificar el certificado y confirmar con el responsable SIFEN si tiene cualquier validez fuera de pruebas.
2. Revocarlo/rotarlo; al estar en Git debe considerarse expuesto aunque el repositorio sea privado.
3. Provisionar el reemplazo fuera del árbol mediante secret manager, volumen de solo lectura o mecanismo equivalente de la plataforma.
4. Configurar solo una ruta externa y el secreto correspondiente por variables/secret store.
5. Excluir extensiones sensibles (`*.p12`, `*.pfx`, `*.pem`, `*.key`, `*.jks`, `*.keystore`) de Git y del contexto/artefacto de build.
6. Verificar el JAR, capas Docker y builds móviles con una prueba automática de ausencia.
7. Coordinar la purga del historial Git con todos los colaboradores. Reescribir historial es disruptivo y no se ejecutará automáticamente.
8. Rotar primero; purgar historial después. Purgar sin rotación no invalida copias existentes.

Acciones externas pendientes: autoridad/cuenta SIFEN, nuevo certificado y decisión coordinada sobre historial.

## 3. Secretos y configuración

### Estado encontrado

- `.env`, `.env.commercial`, `secrets.local.ps1` y `email.local.ps1` están ignorados.
- Los ejemplos exponen nombres de variables, lo cual es correcto si sus valores son placeholders.
- Los patrones actuales no cubren suficientemente archivos como `.env.production`, `.env.development.local`, credenciales Firebase/Google/Apple ni todas las extensiones de claves.
- Producción ya deshabilita Swagger, SQL logging y stack traces, y deshabilita `flyway clean`.

### Controles requeridos

- Ignorar `.env*` y volver a permitir de forma explícita solo `*.example` sanitizados.
- Añadir reglas para `google-services.json`, `GoogleService-Info.plist`, service accounts y credenciales EAS/Firebase.
- Incorporar secret scanning en pre-commit/CI y protección de push en el proveedor Git.
- Mantener separación entre usuario Flyway y rol runtime de la aplicación.
- Validar al iniciar producción que JWT, cifrado, DB, bootstrap admin y certificados no usan defaults/placeholders.
- No usar variables `EXPO_PUBLIC_*` para secretos; se incorporan al bundle móvil.
- Documentar rotación, propietario, vigencia y recuperación de cada secreto sin registrar su valor.

## 4. Autenticación y sesiones

### Estado posterior a Fases 2 y 12

- V35 persiste sesiones refresh hasheadas, rota cada refresh, detecta reutilización y permite revocación/logout.
- Cambiar o resetear contraseña revoca sesiones activas.
- Email se normaliza antes de autenticación/registro y login conserva rate limiting.
- El móvil guarda access token en memoria y refresh token en SecureStore con acceso solo mientras el dispositivo está desbloqueado.
- El bloqueo biométrico fuerte es opcional, protege la UI local y se elimina al cerrar/vencer la sesión.
- Persisten como mejoras futuras la verificación de correo, recuperación pública completa y rate limiting combinado por IP/dispositivo/riesgo.

### Diseño objetivo

- Access token corto en memoria del cliente.
- Refresh token aleatorio o JWT con `jti`, almacenando únicamente hash/identificador de sesión en backend.
- Rotación en cada refresh y detección de reutilización de un token anterior.
- Sesiones con usuario, tenant, dispositivo lógico, creación, último uso, expiración, revocación y motivo.
- Logout de sesión actual y opción de cerrar todas las sesiones.
- Invalidar sesiones al cambiar contraseña, desactivar cuenta o detectar reutilización.
- Comparar refresh de forma atómica para que dos renovaciones concurrentes no produzcan dos cadenas válidas.
- Normalizar email con `trim` + lowercase/estrategia Unicode definida y aplicar unicidad coherente en DB.
- Respuestas de registro/recuperación que no permitan enumerar cuentas.
- Rate limiting combinado por cuenta normalizada, IP/red, dispositivo/riesgo y endpoint, con métricas y ventanas razonables.
- Recuperación de contraseña mediante token de un solo uso, corto, hasheado y revocable.
- Verificación de email sin bloquear de forma innecesaria el browsing, pero exigible antes de acciones de riesgo según política.

### Móvil

- Refresh token solo en Expo SecureStore nativo; en web permanece únicamente en memoria. Implementado en Fase 2.
- Access token solo en memoria y renovación single-flight; logout revoca en servidor y limpia localmente incluso ante fallo de red. Implementado en Fase 2.
- Access token solo en memoria; nunca AsyncStorage.
- Biometría protege el desbloqueo local, no reemplaza autenticación backend.
- Interceptor single-flight: una renovación, cola de requests, un retry máximo y logout si falla.
- No incluir token sensible en URL, deep link, analytics o crash report.
- Fase 12: biometría fuerte opcional, bloqueo tras 30 segundos en background y salida segura por logout.
- Fase 12: cache privado, checkout y badge se limpian al cambiar/cerrar/vencer sesión.

## 5. Multi-tenant y autorización

### Riesgos actuales

- Acceso anónimo usa tenant 1, por lo que login/registro/refresh/catálogo no resuelven una tienda real.
- RLS se anula si la conexión runtime usa superusuario o `BYPASSRLS`.
- FKs relacionan solo IDs y muchos `tenant_id` conservan `DEFAULT 1`.
- `TenantContext` basado en `ThreadLocal` no se propaga automáticamente a schedulers/workers.
- La restricción de caja abierta es global y no tenant-aware.

### Controles requeridos

- Elegir un identificador público de tienda no falsificable por sí solo: host/subdominio, slug validado o código resuelto en backend.
- Incluir tenant firmado en access y refresh, pero validar que usuario/sesión continúan perteneciendo al tenant.
- Establecer el tenant del refresh antes de consultar al usuario.
- Derivar identidad/tenant siempre de SecurityContext/sesión, nunca del JSON.
- Usar rol runtime sin `SUPERUSER`, `BYPASSRLS` ni ownership que eluda las políticas; verificarlo en startup/deploy.
- Separar rol de migración y rol de aplicación con privilegios mínimos.
- Quitar defaults de tenant después de backfill y agregar integridad compuesta `(tenant_id, id)` en relaciones críticas.
- Habilitar y forzar RLS en cada tabla nueva, incluidas outbox, chat, push y notificaciones.
- Workers globales iteran tenants de forma explícita y auditable; no mantienen un tenant residual entre trabajos.
- Tests obligatorios: tenant A nunca lee/escribe recursos de B por URL, body, header, WebSocket ni trabajo asíncrono.

## 6. Catálogo y exposición de datos

### Estado posterior a Fase 3

Corregido: los GET públicos usan `ProductoPublicResponse`, sin costo, margen ni inventario exacto, y el detalle exige activo/visibilidad. El contrato administrativo está separado y protegido por rol. Backend y móvil tienen pruebas que fallan si reaparecen campos internos.

### Controles vigentes y pendientes

- [x] Crear `ProductoPublicResponse` con solo datos necesarios para compra.
- [x] Mantener un DTO administrativo separado protegido por rol.
- [x] Entregar disponibilidad comercial booleana, no costo/margen ni cantidades internas.
- [x] Aplicar tenant, activo y visibilidad tanto al listado como al detalle.
- No inferir autorización por ocultamiento UI.
- [x] Añadir tests de serialización/contrato que fallen si un campo interno reaparece.
- [x] Declarar seguridad OpenAPI por operación, sin Bearer global sobre rutas públicas.

## 7. Checkout, inventario e idempotencia

### Riesgos actuales

- Cantidades sin máximo y suma de duplicados susceptible a overflow.
- No hay `Idempotency-Key`; un retry puede duplicar pedido y reserva.
- Reservas pendientes no expiran.
- Faltan constraints SQL para cantidad/stock/importes.
- Confirmar, cancelar y cobrar no tienen un control de concurrencia uniforme.

### Controles requeridos

- Limitar cantidad por línea, cantidad de líneas y tamaño del request.
- Usar suma exacta (`Math.addExact`) o tipo suficientemente amplio y rechazar overflow.
- Revalidar producto, tenant, activo, visibilidad, stock y precio únicamente en backend.
- Aceptar `Idempotency-Key` opaco, scoped por tenant+usuario+operación, con hash de request y respuesta persistida.
- Misma clave + mismo payload devuelve el resultado original; misma clave + payload distinto devuelve conflicto.
- No reintentar automáticamente POST crítico sin idempotencia.
- Añadir `CHECK cantidad > 0`, stocks no negativos e invariantes de importes mediante nueva migración.
- Añadir expiración de reserva y worker tenant-aware idempotente.
- Aplicar lock/versionado a transiciones de pedido/pago; establecer unicidad de factura por pedido/tenant según regla comercial.
- Mantener snapshot de nombre/código/unidad/precio/IVA/descuento usado en el pedido.

## 8. Pagos

- Abstraer `PaymentProvider` en backend; el móvil no llama directamente a bancos para confirmar la verdad comercial.
- Mantener `pagos` como asientos capturados y crear intentos/transacciones separados.
- Persistir proveedor, identificador externo, idempotency key, estado, monto, moneda y timestamps.
- Deduplicar webhooks con una clave única y verificar firma, timestamp y replay.
- Nunca almacenar PAN completo, CVV, contraseña bancaria ni payloads crudos con secretos.
- Respuestas/provider logs deben estar sanitizados y sujetos a retención.
- No presentar una integración mock como pago real.

## 9. Outbox, correo, push y eventos

### Estado posterior a Fases 10 y 11

V41 implementa outbox transaccional durable con canales independientes, retry, backoff, idempotencia y RLS. V42 agrega el canal interno y el centro de notificaciones sin volver a acoplar SMTP/Expo a las transacciones comerciales.

### Diseño objetivo

1. La transacción comercial guarda el cambio de dominio y un evento outbox.
2. Después del commit, un worker reclama eventos con locking seguro.
3. Consumidores independientes crean entrega email, push, notificación interna y auditoría.
4. Cada entrega tiene clave idempotente, intentos, backoff con jitter, próximo intento y error sanitizado.
5. La caída de un proveedor no cambia un pedido confirmado ni bloquea checkout.
6. Payloads outbox contienen referencias/datos mínimos y nunca secretos.
7. Dead-letter o estado agotado genera alerta y permite replay controlado.

Los workers deben establecer y limpiar tenant explícitamente.

## 10. Chat, WebSocket y archivos

- Autenticar el handshake/conexión y autorizar cada destino/mensaje.
- Derivar usuario, tenant y rol de la autenticación; ignorar equivalentes enviados por dispositivo.
- Comprobar participante/permiso en historial REST y eventos WebSocket.
- Limitar tamaño, frecuencia y cantidad; cerrar sesiones inválidas.
- Usar `clientMessageId` único por remitente/conversación para deduplicar retry/reconexión.
- Paginar historial y persistirlo; WebSocket no reemplaza REST de recuperación.
- Para adjuntos: tamaño, MIME real, extensión, dimensiones, malware según riesgo y nombre de storage aleatorio.
- Bloquear ejecutables, path traversal y acceso directo no autorizado.
- `FileStorageProvider` entrega referencias firmadas/autorizadas, no rutas físicas.
- No colocar JWT en query string si el cliente/protocolo permite headers de autenticación seguros.

## 11. SIFEN y documentos fiscales

- Toda generación, firma, envío, consulta, CDC y KUDE permanece en backend.
- Nunca incluir `.p12`, password, CSC, private key o XML sensible dentro de artefactos cliente.
- Congelar emisor, receptor y conceptos al emitir.
- Persistir XML firmado o referencia durable más hash, CDC, ambiente, estado, respuesta/protocolo e intentos.
- Separar estado comercial/factura del estado SIFEN.
- Enviar/consultar mediante outbox y operaciones idempotentes.
- El KUDE debe representar el documento inmutable emitido, no regenerarse con datos maestros actuales.
- Auditar acceso y descarga sin registrar contenido sensible completo.

## 12. Errores, logs y observabilidad

- Unificar errores MVC, autenticación y autorización en `application/problem+json` con `code` estable, mensaje localizado, status, path/correlation ID y detalles seguros.
- Nunca devolver stack trace, SQL, nombre interno de clase ni excepción Java.
- Redactar `Authorization`, cookies, password, access/refresh token, claves, configuración SMTP/SIFEN y datos de tarjeta.
- Deshabilitar logging de cuerpos para login/refresh/password y respuestas de tokens.
- Los tests observados imprimieron datos de autenticación con DEBUG: añadir prueba/guard de logging y usar niveles explícitos por perfil.
- No registrar RUC/teléfono/email completos salvo necesidad operacional legítima; aplicar masking y retención.
- Métricas: login/rate limit, refresh reuse, checkout idempotency, stock conflicts, outbox lag/retries, push invalid tokens, WebSocket rejects y fallos de tenant.
- Crash reporting móvil recibe errores sanitizados, sin tokens ni payloads completos.

## 13. Red y plataforma móvil

- Producción acepta únicamente API HTTPS; rechazar configuración `http://` y `localhost` al compilar/iniciar perfil production.
- Desarrollo físico usa una URL LAN configurable y documentada.
- No desactivar validación TLS ni aceptar certificados arbitrarios.
- CORS permite solo orígenes web conocidos; la app nativa no justifica `*` con credenciales.
- Pedir notificaciones, biometría, cámara/GPS solo al activar una función y con contexto previo.
- GPS es opcional para direcciones/checkout.
- AsyncStorage queda limitado a preferencias, theme, carrito scoped y cache no sensible.
- Deep links validan rutas/IDs y vuelven a comprobar auth/ownership en backend.
- El árbol autenticado queda oculto por el gate biométrico y el cache privado se elimina al finalizar la sesión.
- Universal/App Links solo se habilitan cuando exista dominio oficial verificable.

## 14. CI/CD y supply chain

- [x] Backend `clean verify`, frontend `check` y mobile con `npm ci`, typecheck, lint, Jest, Maestro estático, Expo config, Doctor y export Android.
- [x] Migraciones PostgreSQL forman parte del gate backend (42 migraciones, `MigracionesIT` 15/15 al cerrar Fase 14).
- [x] Secret scan, dependency audit crítica, SBOM CycloneDX y revisión automática de licencias.
- [x] Actions fijadas por SHA, `permissions` mínimos, timeouts y concurrency.
- [x] `npm ci` sin flags divergentes en CI, Docker y validación local.
- [x] JAR, bundle web y export Android se inspeccionan por extensiones y marcadores sensibles; imágenes Docker quedan para el runner que disponga de Docker.
- [x] Dependabot semanal para Actions, Maven y ambos árboles npm.
- [ ] CodeQL está configurado; en el repositorio privado requiere GitHub Advanced Security y `ENABLE_CODEQL=true`.
- Firmas Android/iOS y credenciales de distribución permanecen en EAS/secret store, nunca en Git.
- Builds de PR no reciben secretos de producción.
- Generar evidencia de provenance/artefacto y conservar hashes cuando la plataforma lo permita.

Procedimiento y baseline: `docs/security/CI_SUPPLY_CHAIN.md`.

## 15. Pruebas de seguridad mínimas

- Catálogo público no serializa campos internos ni devuelve productos no visibles.
- Overflow, cantidad cero/negativa, carrito enorme y duplicados se rechazan.
- Misma idempotency key no duplica pedido; payload distinto produce conflicto.
- Dos cobros concurrentes no duplican factura/movimiento.
- Refresh rota; reuse revoca cadena; cambio de password/logout invalida sesiones.
- Tenant A no accede a B por REST, ID alterado, header, body, WebSocket ni worker.
- Rol runtime de integración no puede eludir RLS.
- Outbox sobrevive rollback/crash, reintenta y no duplica entrega.
- Upload rechaza MIME falso, traversal, tamaño excesivo y ejecutables.
- Logs y errores no contienen secretos/tokens/passwords.
- Config production rechaza HTTP, localhost, defaults y secretos ausentes.
- Escaneo de artefactos confirma ausencia de claves/certificados.

## 16. Relación con OWASP MASVS

| Área MASVS | Controles TechStore |
| --- | --- |
| STORAGE | SecureStore para refresh; AsyncStorage no sensible; artefactos sin claves |
| CRYPTO | TLS; claves/certificados externalizados; no criptografía casera |
| AUTH | sesiones rotables/revocables, rate limit, recuperación segura, biometría local auxiliar |
| NETWORK | HTTPS obligatorio, validación TLS, URLs por ambiente, sin tokens en URL |
| PLATFORM | permisos just-in-time, deep links validados, storage/clipboard/logs seguros |
| CODE | TypeScript estricto, validación Zod/servidor, supply-chain y tests |
| RESILIENCE | no se confía en el cliente; integridad/idempotencia/attestation opcional según riesgo |
| PRIVACY | minimización, masking, retención y consentimiento contextual |

## 17. Orden de ejecución

1. Rotar/externalizar certificado y cerrar fuga del DTO público.
2. Corregir baseline, logging de auth y validación/overflow de checkout.
3. Resolver tenant anónimo y sesiones refresh/logout.
4. Implementar idempotencia, expiración de reserva e invariantes DB.
5. Crear outbox antes de incorporar push/chat/proveedores externos.
6. Agregar funciones móviles por migraciones incrementales con RLS y tests de aislamiento.
7. Habilitar CI móvil, escaneo de artefactos y builds EAS solo cuando los gates anteriores estén verdes.
