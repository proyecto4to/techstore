# Auditoría inicial de TechStore

Fecha: 2026-08-20  
Estado: COMPLETED  
Alcance: monorepo `techstore` en `main`, antes de crear el cliente Flutter.

## 1. Resumen ejecutivo

TechStore es un e-commerce/ERP multi-tenant funcional, con Web React, API Spring Boot y PostgreSQL como fuente única de verdad. Ya contiene catálogo, checkout idempotente, inventario con bloqueo pesimista, ventas directas, compras, caja, facturación/PDF, notas de crédito, proveedores, envíos, clientes, usuarios, auditoría, reportes, contabilidad, correo, push, notificaciones internas y chat STOMP.

La base es apta para ampliarse a Flutter sin crear un backend ni una base paralelos. El trabajo principal no es reconstruir el sistema, sino completar el contrato API, extender eventos en tiempo real más allá del chat, diseñar el cliente Flutter y validar integración Web ↔ Mobile.

El repositorio referencia una app móvil Expo externa (`proyecto4to/techstore-mobile`). No hay código móvil dentro del monorepo actual. El objetivo solicitado exige Flutter; la arquitectura deberá definir el nuevo cliente Flutter sin mezclar ni reutilizar código Expo como si fuera Flutter.

## 2. Inventario del repositorio

| Área | Contenido |
|---|---|
| `.github` | CI, CodeQL condicional y Dependabot |
| `docker` | Validación de configuración, rol PostgreSQL restringido y plantillas comerciales |
| `docs` | Contrato OpenAPI móvil existente y hardening de seguridad |
| `scripts` | Secret/supply-chain scan y revisión de licencias/SBOM |
| `techstore-backend` | Spring Boot 3.5.16, Java 21, Maven |
| `techstore-frontend` | React 18, Vite 8, Tailwind, Zustand y TanStack Query |
| `tools` | Maven y PostgreSQL portables, ignorados por Git |
| raíz | Docker Compose, launchers PowerShell, backup/restore, README y manual |

Métricas verificadas:

- 584 archivos inspeccionados por el control de seguridad del repositorio.
- 318 clases Java productivas y 63 clases Java de prueba.
- 26 controladores REST y 125 mappings detectados al levantar Spring.
- 44 servicios, 33 repositorios JPA y 45 entidades.
- 42 migraciones Flyway.
- 22 páginas Web JSX y 18 servicios Web.
- Árbol Git inicialmente limpio en `main...origin/main`; no existe `AGENTS.md` local adicional.

## 3. Frontend Web

### Stack y arquitectura

- React 18 con React Router 7.
- Vite 8 y Tailwind CSS.
- Axios para API, Zustand para sesión/carrito y TanStack Query para datos remotos.
- STOMP (`@stomp/stompjs`) para chat en tiempo real.
- Interceptor Axios agrega Bearer access token y rota refresh token ante 401.
- El store de autenticación persiste access/refresh token en almacenamiento del navegador; Mobile deberá usar secure storage, no copiar este mecanismo.

### Rutas y funciones

- Públicas: catálogo, detalle de producto, login, registro y carrito.
- Cliente autenticado: pedidos y cuenta.
- Administración: inicio, productos, facturas, caja, usuarios, compras, venta directa, empresa, reportes, contabilidad, correos, mensajes y auditoría.
- Protección de rutas por autenticación y roles en UI; el backend conserva la autoridad real.

### Observaciones

- No hay PWA/offline ni cola de mutaciones Web.
- El chat recibe actualizaciones por STOMP; productos, stock, ventas y otras entidades no tienen suscripciones Web generales.
- El bundle actual compila; JS principal ~496 kB sin comprimir (~139 kB gzip), candidato a code splitting en Fase 13.
- Vitest con forks agotaba el tiempo de arranque en Windows/OneDrive. Se fijó `threads` con un worker, manteniendo la suite determinista y estable.

## 4. Backend y lógica de negocio

### Stack

- Spring Boot 3.5.16 / Java release 21.
- Spring MVC, Data JPA, Security, Validation, Actuator, Mail y WebSocket.
- JWT JJWT 0.12.5, MapStruct, Lombok, OpenAPI, Flyway, OpenPDF y ZXing.
- PostgreSQL 15; pruebas de integración con PostgreSQL embebido Zonky 15.10.

### Módulos

| Módulo | Responsabilidades principales |
|---|---|
| auditoría | cambios por entidad/usuario y consulta filtrada |
| caja | apertura, movimientos, resumen, cierre y PDF |
| catálogo | productos, marcas, modelos, imágenes, favoritos y reposición |
| compras | proveedores, ofertas, comparación, compra, recepción, pago, envío y comprobante |
| contabilidad | libros IVA, resumen, PDFs y registro DNIT ZIP |
| direcciones | CRUD de direcciones propias |
| empresa | emisor fiscal, pendientes y puntos de expedición |
| envíos | cotización, tarifas, tracking y eventos |
| facturación | pagos, facturas, KuDE/XML, notas de crédito y SIFEN demostrativo |
| mensajería | conversaciones, historial, lectura y STOMP |
| notificaciones | correo, dispositivos, push Expo, centro interno y outbox |
| reportes | dashboard, ventas, stock bajo y PDF |
| usuarios | login, registro, refresh, logout, perfiles, roles y administración |
| ventas | carrito, checkout, pedidos y venta directa |

### Reglas críticas verificadas

- Backend autoritativo para precios, stock, pagos, permisos, tenant y fiscalidad.
- Checkout idempotente y reserva/compromiso de stock.
- Bloqueo pesimista evita sobreventa concurrente.
- Confirmación descuenta stock real y cancelación libera el comprometido.
- Venta directa valida stock y registra pago/caja/factura.
- Compras modifican stock al recibir, no al crear.
- Validaciones fiscales paraguayas, dinero/IVA, números de comprobante y estados están en backend.
- Errores se serializan como RFC 7807 `ProblemDetail`, con validaciones por campo e i18n ES/EN.

## 5. Base de datos

### Esquema

36 tablas creadas por Flyway:

`roles`, `usuarios`, `clientes`, `proveedores`, `marcas`, `modelos`, `productos`, `autonumerador`, `pedidos`, `pedido_detalles`, `pagos`, `facturas`, `movimiento_caja`, `audit_log`, `notas_credito`, `nota_credito_detalles`, `cierres_caja`, `correos`, `configuracion`, `aperturas_caja`, `intento_login`, `tenants`, `proveedor_productos`, `puntos_expedicion`, `compra_comprobantes`, `sesiones_refresh`, `favoritos`, `direcciones_entrega`, `tarifas_envio`, `envios`, `envio_eventos`, `conversaciones`, `mensajes`, `dispositivos_moviles`, `notification_outbox`, `notificaciones_usuario`.

### Integridad y aislamiento

- PK `BIGSERIAL` o claves naturales controladas según tabla.
- FK explícitas entre usuarios/roles, catálogo, pedidos/detalles, pagos, facturas, caja, compras, envíos, chat y notificaciones.
- 67 índices explícitos, además de PK/UK.
- Enums PostgreSQL para estados y tipos comerciales/fiscales.
- Checks de importes, IVA, pesos, coordenadas, estados, longitudes y rangos.
- Función `tenant_actual()` basada en `app.tenant_id`.
- RLS para tablas core mediante migración dinámica y para módulos nuevos mediante políticas explícitas.
- La conexión recibe el tenant desde un JWT firmado; no se acepta un header libre del cliente.
- No se detectaron triggers, procedures ni views; la lógica transaccional vive en servicios y repositorios.

PostgreSQL central es la única fuente de verdad. No existe una base Mobile local en este repositorio; cualquier caché/cola Flutter deberá reconciliar contra la API.

## 6. API

### Contrato y transporte

- REST bajo `/api/v1`.
- JSON para CRUD; multipart para imagen de producto.
- Descargas PDF, XML y ZIP para facturación, reportes, caja y contabilidad.
- Paginación Spring normalizada mediante `PageResponse` en catálogo, pedidos, compras, chat, caja y auditoría.
- OpenAPI de Spring disponible en desarrollo y deshabilitado en producción.
- El contrato `docs/api/mobile-auth.openapi.yaml` cubre auth, catálogo, favoritos, carrito, direcciones, envíos, chat y pedidos, pero no todas las funciones administrativas del alcance Flutter.

Familias: `auth`, `usuarios`, `productos`, `marcas`, `favoritos`, `carrito`, `pedidos`, `ventas`, `pagos`, `facturas`, `notas-credito`, `proveedores`, `compras`, `direcciones`, `envios`, `caja`, `empresa`, `reportes`, `contabilidad`, `correos`, `dispositivos`, `notificaciones`, `conversaciones`, `dashboard` y `auditoria`.

### Tiempo real

- STOMP en `/ws`; prefijo de envío `/app`; destinos `/user/queue/...`.
- JWT validado en CONNECT/SEND/SUBSCRIBE, con tenant y límites de frame/buffer.
- Implementado para chat y confirmaciones de lectura.
- No existen aún eventos generales `PRODUCT_*`, `STOCK_UPDATED`, `SALE_*`, `CLIENT_*`, etc.

## 7. Autenticación, autorización y seguridad

### Controles presentes

- BCrypt; access JWT corto y refresh JWT rotativo/revocable persistido como hash.
- Rate limiting de login y mensajes genéricos contra enumeración.
- API stateless; refresh token no autentica operaciones de negocio.
- Roles: `Admin`, `Cajero`, `Cliente`.
- `@PreAuthorize` en administración, caja, compras, facturación, empresa, reportes y auditoría.
- DTO público de catálogo sin costo/margen/stock interno.
- RLS multi-tenant, filtros de aplicación y tests de aislamiento.
- CORS configurable; Actuator limitado y Swagger apagado en producción.
- Perfil `prod` fail-closed para DB/JWT/CORS/cifrado y sin stacktraces.
- Secret scan, SBOM/licencias, CodeQL condicional, Actions fijadas por SHA y Dependabot.

### Hallazgos

| ID | Severidad | Hallazgo | Tratamiento previsto |
|---|---|---|---|
| AUD-SEC-01 | Media | Certificado SIFEN de prueba `.p12` versionado en recursos main. No es fiscal real, pero contiene clave privada de prueba. | Externalizar/mover a fixture y excluir de Release (Fases 14/17). |
| AUD-SEC-02 | Media | Upload confía en `Content-Type`; limita tamaño y genera nombre seguro, pero no valida magic bytes/decodificación. | Validar firma y re-encode seguro (Fase 14). |
| AUD-SEC-03 | Media | Tokens Web persisten en almacenamiento del navegador. | Analizar XSS; Flutter usará Keystore/secure storage (Fases 07/14). |
| AUD-SEC-04 | Baja | `spring.jpa.open-in-view` está habilitado por defecto. | Deshabilitar y ajustar cargas (Fases 03/13/14). |
| AUD-SEC-05 | Alta para producción fiscal | SIFEN es demostrativo, no homologado y sin transmisión DNIT. | No declarar validez legal sin integración/homologación externa. |
| AUD-SEC-06 | Condicional | CodeQL privado requiere GitHub Advanced Security y variable habilitante. | Mantener gates alternativos y registrar capacidad externa. |

No se detectaron secretos comerciales reales tracked. `.env`, keystores reales, service accounts y credenciales locales están ignorados. Los defaults de desarrollo son explícitos y el perfil `prod` los reemplaza o rechaza.

## 8. Configuración, despliegue e integraciones

- Docker Compose separa perfiles `local`, `commercial-local` y `commercial`.
- PostgreSQL se publica por defecto solo en loopback y usa rol de aplicación limitado más rol Flyway.
- Launchers PowerShell soportan PostgreSQL/Maven portables, inicio, parada, backup y restore.
- Uploads y base se almacenan fuera del repositorio/OneDrive.
- SMTP real es opcional; sin credenciales queda simulado.
- Push usa proveedor Expo opcional y transactional outbox con reintentos/backoff.
- SIFEN genera XML/KuDE demostrativos; no transmite ni está homologado.
- No existe configuración Firebase/FCM nativa Flutter aún.
- CI valida backend, Web, secretos, dependencias, SBOM, licencias y artefactos; deberá ampliarse a Flutter.

## 9. Pruebas y calidad

| Alcance ejecutado el 2026-08-20 | Resultado |
|---|---|
| Maven unitarios con JDK 22/release 21 | 317 passed, 0 failures |
| Failsafe con PostgreSQL 15.10 | 37 passed, 0 failures |
| Flyway en base vacía | 42/42 migraciones |
| Frontend `npm run check` | lint OK, 12 tests OK, build OK |
| `npm audit` final | 0 vulnerabilidades |
| Security scan | OK, 584 archivos; warning SIFEN conocido |
| `git diff --check` | sin errores; aviso LF/CRLF local |

Problemas corregidos:

- `JAVA_HOME` local era Java 17; las pruebas se ejecutaron con JDK 22 compatible/release 21.
- `mvn clean verify` no borró el JAR porque TechStore estaba activo. Sin interrumpirlo se ejecutaron unitarias e integraciones por separado, ambas exitosas.
- Vitest forks agotaban el arranque en Windows/OneDrive; se configuró un worker de hilos.
- `nanoid 3.3.16` vulnerable se actualizó a 3.3.18; audit quedó limpio.

## 10. Código pendiente, duplicado y muerto

- No hay `TODO/FIXME/HACK` propio en producción; los encontrados pertenecen a XSD oficiales SIFEN de test.
- Los `return null` revisados son normalización opcional, ramas no configuradas, collectors o fallbacks; no hay `UnsupportedOperationException` ni `NOT_IMPLEMENTED` productivo.
- Directorios frontend vacíos (`cart`, `order`, `payment`) parecen restos estructurales y deberán consolidarse.
- La lógica crítica está centralizada; no existe una segunda implementación Mobile en el monorepo.
- El hardening conserva referencias a fases de una app Expo externa; deberá reconciliarse con Flutter.

## 11. Riesgos y decisiones para fases siguientes

1. Definir alcance Mobile por rol y función.
2. Definir ubicación Flutter; recomendación inicial: `techstore-mobile/` dentro del monorepo.
3. Ampliar OpenAPI y generar cliente tipado.
4. Crear eventos de dominio sobre STOMP para entidades que exigen inmediatez.
5. Clasificar offline por operación; ventas/pagos/stock no asumirán éxito local.
6. Seleccionar FCM o adaptar push; Expo Push no se heredará automáticamente.
7. Mantener SIFEN como limitación de release hasta homologación real.
8. Hardware, cuentas push, firma y Play Store son dependencias externas de fases posteriores.

## 12. Cobertura

- [x] Estructura, archivos, frontend, backend y assets.
- [x] Base, relaciones, índices, RLS y migraciones.
- [x] API, auth, roles, permisos, errores, paginación, uploads y descargas.
- [x] Tiempo real, correo, push, outbox e integraciones.
- [x] Variables, Docker, scripts, CI y release actual.
- [x] Tests, build, TODO/FIXME/HACK, código potencialmente muerto y secretos.
- [x] Riesgos de seguridad y limitaciones operativas.

## 13. Delta de auditoría final

Este documento conserva el baseline de Fase 00. La comparación completa posterior a la expansión, incluida la matriz de 67 funciones y los bloqueos reales, está en `PHASE_19.md`. El estado final automatizable fue revalidado el 2026-08-23 contra OpenAPI, rutas Mobile, pruebas, seguridad y artefactos; no modifica retroactivamente los conteos históricos de esta auditoría inicial.

FASE 00 cumple sus criterios. Los hallazgos de implementación quedan trazados para fases posteriores.
