# TechStore Mobile — auditoría de Fase 0

Fecha: 2026-08-10  
Estado: auditoría completada; Fases 1 y 2 móviles completadas posteriormente  
Repositorio auditado: `C:\Users\Dell Vostro\OneDrive\Documentos\GitHub\techstore`

## 1. Alcance y método

Esta auditoría cubre la raíz Git real, backend, frontend web, migraciones, seguridad, multi-tenant, contratos HTTP, operación, CI y baseline de pruebas. No se inspeccionó ni modificó `SechStore`. No se mostraron valores de secretos ni se modificaron archivos de negocio durante la auditoría.

El repositorio estaba previamente modificado y con archivos sin seguimiento. La rama `main` se encontraba 13 commits por delante de `origin/main`; esos cambios se consideran trabajo existente y deben preservarse.

## 2. Resultado ejecutivo

TechStore ya ofrece una base reutilizable valiosa: catálogo paginado, autenticación JWT, refresh token, usuarios y roles, pedidos transaccionales, reserva de stock, pagos de caja, facturación, correo, auditoría, PostgreSQL con Flyway y aislamiento multi-tenant mediante Hibernate y RLS.

No es seguro conectar una aplicación móvil comercial al contrato actual sin resolver primero estos bloqueantes:

1. El catálogo público devuelve el mismo DTO administrativo y expone costo, margen e inventario interno.
2. El checkout no tiene idempotencia ni límites suficientes y la suma de cantidades puede desbordar un `Integer`.
3. Login, refresh, registro y catálogo anónimo se ejecutan en el tenant por defecto; el acceso anónimo multi-tenant todavía no tiene resolución de tienda.
4. Los refresh tokens no tienen sesión persistida, rotación, revocación ni logout de servidor.
5. Un certificado SIFEN `.p12` está versionado y termina empaquetado en el JAR y la imagen Docker.
6. La primera ejecución del baseline backend detectó que `MigracionesIT` esperaba 33 migraciones mientras el árbol ya contenía V34. El contador se actualizó deliberadamente a 34 y el ciclo completo quedó en verde.

La Fase 1 se implementó como una fundación visual aislada, sin conectar operaciones reales al contrato inseguro. Los bloqueantes de catálogo, checkout, tenant y credenciales siguen siendo gates para la integración de Fase 2 y para cualquier uso comercial.

## 3. Arquitectura encontrada

```text
React/Vite web ───────┐
                     ├── HTTP/JSON ── Spring Boot API ── JPA/Flyway ── PostgreSQL
App móvil (Fase 1) ─┘                    │                    │
                                         ├── SMTP             └── RLS por tenant
                                         ├── PDF/KUDE
                                         └── SIFEN demostrativo local
```

- Backend monolítico modular por dominio en `techstore-backend`.
- Frontend React/Vite en `techstore-frontend`.
- PostgreSQL como fuente de verdad; Flyway es el único mecanismo válido de evolución del esquema.
- Aplicación stateless con Bearer JWT y refresh JWT.
- `TenantFilter` establece el tenant de la petición y `TenantDataSource` lo propaga a PostgreSQL mediante `set_config`.
- El frontend tiene una única instancia Axios, Zustand para sesión/carrito y TanStack Query.
- Docker Compose y scripts PowerShell cubren operación local/comercial.
- `techstore-mobile` existe desde la Fase 1 como base Expo universal; todavía no consume la API real.

## 4. Versiones reales

### Backend

| Componente | Versión/configuración |
| --- | --- |
| Java objetivo | 21 |
| JDK local observado | 22.0.1 |
| Spring Boot | 3.5.16 |
| Maven local | 3.9.9, incluido en `tools` |
| PostgreSQL JDBC | 42.7.13 |
| JJWT | 0.12.5 |
| Springdoc | 2.8.17 |
| Flyway | gestionado por Spring Boot |

### Frontend web

| Componente | Versión resuelta |
| --- | --- |
| Node local | 24.18.0 |
| npm local | 11.16.0 |
| React / React DOM | 18.3.1 |
| React Router | 7.18.2 |
| TanStack Query | 5.101.2 |
| Axios | 1.18.1 |
| Zustand | 4.5.7 |
| Vite | 8.1.5 |
| Vitest | 4.1.10 |
| ESLint | 10.8.0 |

El `package.json` requiere Node `>=20.19.0` y npm `>=10.8.2`; CI y Docker usan Node 22.

## 5. Baseline reproducible

### Backend

Comando:

```powershell
& '.\tools\apache-maven-3.9.9\bin\mvn.cmd' verify
```

Resultado final del 2026-08-10:

- la primera ejecución detectó el contador desactualizado de `MigracionesIT` (33 esperado frente a V34 presente);
- se actualizó únicamente esa expectativa intencional a 34;
- compilación: correcta;
- unitarios/Surefire: 265 tests, 0 fallos, 0 errores;
- integración/Failsafe: 29 tests, 0 fallos, 0 errores;
- resultado Maven: `BUILD SUCCESS`.

Durante integración se observaron cuerpos de login y respuestas de autenticación en logs DEBUG. Aunque los datos pertenecen al entorno de prueba, los filtros de logging deben impedir serializar contraseñas, tokens y PII en cualquier perfil.

### Frontend web

Comando:

```powershell
npm run check
```

Resultado:

- ESLint: correcto, sin advertencias;
- Vitest: 5 archivos y 10 tests aprobados;
- build Vite: correcto, 1.940 módulos;
- bundle JS principal: 465,37 kB, 129,98 kB gzip.

La cobertura web es todavía estrecha: faltan pruebas del interceptor/refresh concurrente, catálogo, checkout, pedidos, errores, permisos y navegación protegida.

## 6. Migraciones y esquema

La auditoría original cerró en V34. Fase 2 agregó V35 y Fase 3 agregó `V36__favoritos.sql`; la siguiente migración libre es V37 y nunca deben editarse V1–V36 para introducir funciones nuevas.

Tablas y capacidades reutilizables:

- usuarios, roles e intentos de login;
- productos, marcas y modelos;
- pedidos, detalles, historial y reserva de stock;
- pagos, facturas y notas de crédito;
- caja, compras y proveedores;
- configuración de empresa y puntos de expedición;
- correos y configuración SMTP;
- auditoría;
- tenant en entidades de negocio y RLS forzado en PostgreSQL.

Brechas del esquema para móvil:

- favoritos;
- direcciones de entrega guardadas y snapshot logístico por pedido;
- idempotencia de checkout;
- expiración de reservas;
- categorías, varias imágenes y búsqueda optimizada;
- envíos B2C y eventos de tracking;
- intentos/transacciones de pago online y deduplicación de webhooks;
- conversaciones, participantes, mensajes, adjuntos y lecturas;
- dispositivos push;
- centro de notificaciones;
- outbox transaccional durable;
- persistencia inmutable del documento electrónico y estado SIFEN.

## 7. API existente y reutilización móvil

Todas las rutas siguientes fueron comprobadas en controladores reales.

### Pública o de cliente

| Área | Endpoints existentes | Evaluación |
| --- | --- | --- |
| Autenticación | `POST /api/v1/auth/registro`, `/login`, `/refresh`; `GET /me`; `PUT /password`, `/datos-fiscales` | Base reutilizable; faltan logout, revocación, recuperación y verificación de email |
| Catálogo | `GET /api/v1/productos`, `/productos/{id}`, `/marcas` | Paginación/búsqueda reutilizables después de separar DTO público y visibilidad |
| Pedidos | `POST /api/v1/pedidos`; `GET /pedidos`, `/pedidos/{id}`; `PUT /pedidos/{id}/cancelar`; `GET /pedidos/{id}/factura/pdf` | Ownership existente; faltan idempotencia, dirección, envío y pago cliente |

El checkout actual recibe únicamente `items[{productoId,cantidad}]` y `notas`. El backend bloquea productos con `PESSIMISTIC_WRITE`, valida disponibilidad, compromete stock y congela precio/IVA. Esa lógica debe seguir siendo la fuente de verdad.

### Backoffice ya disponible

- productos y reposición;
- ventas directas;
- compras, comparación de proveedores, recepción, pago y envío al proveedor;
- proveedores y ofertas;
- caja y cierres;
- pedidos, confirmación y cancelación;
- pagos, facturas, notas de crédito, PDF, documento electrónico y KUDE;
- usuarios;
- empresa y puntos de expedición;
- correo;
- dashboard y reportes;
- auditoría;
- contabilidad y libros fiscales.

Estas familias deben permanecer en el backend/web administrativo; no justifican duplicar reglas en React Native.

## 8. Servicios y código reutilizable

### Backend

- `PedidoService`: transacción, locking de productos, stock comprometido y ownership.
- `Dinero`, `DesgloseIva`, `LineaGravada`: importes e IVA paraguayo.
- `RucParaguayo`: validación fiscal.
- `PagoService`: asientos de pago/facturación/caja; no es todavía un proveedor de pago móvil.
- `CorreoService` y `EmailTemplateService`: entrega y plantillas; deben ejecutarse desde outbox, no en la transacción crítica.
- `DocumentoElectronicoService`, `FacturaElectronicaService` y `KudeService`: base SIFEN demostrativa; nunca trasladar al móvil.
- auditoría existente.
- `TenantContext`, `TenantFilter` y `TenantDataSource`: base del aislamiento; workers asíncronos deberán propagar tenant explícitamente.
- `GlobalExceptionHandler`: base `ProblemDetail`; falta uniformar 401/403 y códigos estables.

### Frontend web

- `src/services/api.js`: Axios único, Bearer y refresh single-flight con `_retry`.
- servicios de auth, productos, pedidos, empresa, caja, correo y administración.
- `authStore`: útil como referencia de flujo, no de storage móvil.
- `cartStore`: útil como referencia funcional, pero no debe copiarse sin scope y revalidación.
- formateadores de moneda/estado de pedido.

## 9. Hallazgos de seguridad y deuda

### Críticos/altos

1. **DTO público de catálogo:** expone costo, fecha de costo, margen, stock real/comprometido/mínimo y reposición. El detalle por ID tampoco exige activo/visible.
2. **Certificado versionado:** `techstore-fe-test.p12` está trackeado, presente en historial, copiado por Maven y empaquetado en Docker. Debe tratarse como comprometido.
3. **Checkout:** cantidad sin máximo, duplicados con `Integer::sum`, sin `Math.addExact`, sin idempotencia ni constraints SQL suficientes.
4. **Refresh/session:** no hay `jti`, sesión persistida, rotación, revocación ni logout. Cambiar contraseña no invalida refresh previos.
5. **Tenant anónimo:** las peticiones sin access token caen al tenant 1. Multiempresa móvil requiere resolución segura de tienda por host/código y refresh que establezca tenant antes de consultar usuario.
6. **RLS operativo:** RLS queda anulado si producción conecta con superusuario o `BYPASSRLS`. El rol de aplicación limitado debe ser un gate de despliegue.
7. **SIFEN mutable:** el documento se regenera con configuración/nombre/email actuales y no persiste XML firmado, CDC ni estado de recepción.
8. **Correo síncrono:** SMTP ocurre antes del registro durable; una caída o rollback puede dejar entrega sin trazabilidad o bloquear flujo comercial.
9. **Concurrencia de cobro/factura:** faltan locks/versionado e invariantes únicas suficientes para evitar doble procesamiento.

### Medios

- registro permite enumeración de cuentas y no tiene rate limit/verificación;
- rate limiter solo por email permite bloqueo dirigido y puede perder incrementos concurrentes;
- email no está normalizado consistentemente y login no limita longitud;
- 401/403 no usan el mismo contrato de error que MVC;
- OpenAPI aplica Bearer global incluso a rutas públicas;
- carrito web persiste productos completos bajo una clave global, sin usuario/tenant/versionado;
- FKs tenant usan solo ID y muchos `tenant_id` conservan `DEFAULT 1`;
- `uk_apertura_abierta` es global y no tenant-aware;
- reservas de pedido no expiran;
- búsqueda `%texto%` no aprovecha el índice b-tree actual;
- Docker, CI y `.gitignore` necesitan mayor paridad/hardening.

El plan detallado de mitigación está en `docs/security/SECURITY_HARDENING.md`.

## 10. Funcionalidades faltantes para la app

- React Native/Expo y navegación;
- almacenamiento seguro y gestión de sesión móvil;
- OpenAPI → TypeScript generado;
- configuración development/preview/production;
- design system Titanium Glass e i18n central;
- home/catálogo móvil rico, búsqueda, filtros y favoritos;
- carrito scoped, revalidación e idempotencia;
- direcciones paraguayas;
- métodos de envío, tracking y timeline;
- abstracción de pagos sin fingir proveedores;
- chat REST + WebSocket/STOMP e inbox web;
- push, dispositivos, centro de notificaciones y deep links;
- outbox transaccional, retries e idempotencia;
- biometría local opcional;
- offline seguro, accesibilidad, observabilidad y E2E;
- perfiles EAS para Android/iOS y CI móvil.

## 11. Estrategia concreta de implementación

### Gate 0 — estabilización antes de la app

1. **Completado:** actualizar deliberadamente el contador de `MigracionesIT` a 34 y repetir `mvn verify`.
2. Crear `ProductoPublicResponse`, usarlo en GET públicos y exigir activo/visible.
3. Limitar ítems/cantidades, detectar overflow, añadir constraints y diseñar `Idempotency-Key`.
4. Definir resolución del tenant para acceso anónimo y refresh.
5. Diseñar sesiones de refresh con hash, `jti`, rotación, revocación y logout.
6. Rotar/externalizar el `.p12`, evitar que entre al JAR/imagen y sanear historial mediante una operación coordinada.
7. Redactar/sanitizar logging de cuerpos y autenticación.

### Migraciones propuestas

| Versión | Objetivo |
| --- | --- |
| V35 | Sesiones refresh seguras: hash, rotación, revocación, detección de reutilización y RLS (implementada en Fase 2) |
| V36 | Favoritos únicos por usuario+producto+tenant con RLS (implementada en Fase 3) |
| V37 | Hardening: idempotencia, reserva con expiración, invariantes/locks, snapshot de producto, caja tenant-aware, constraints e índices |
| V38 | Catálogo rico futuro: categorías, imágenes múltiples, SKU/slug y búsqueda indexada |
| V39 | Direcciones y snapshot de entrega |
| V40 | Envíos B2C y eventos de tracking |
| V41 | Intentos/transacciones de pago y deduplicación de webhooks |
| V42 | Outbox, dispositivos push y notificaciones internas/entregas |
| V43 | Conversaciones, participantes, mensajes, lecturas y adjuntos |
| V44 | Documento electrónico inmutable, CDC, estados e intentos SIFEN |
| V45 | Hardening tenant: quitar defaults y usar integridad/FKs compuestas |

La numeración es una propuesta; antes de crear cada archivo debe volver a comprobarse la última migración presente para evitar colisiones.

### Fase 1 móvil — completada

La fundación Expo Router, TypeScript estricto, configuración por ambiente, perfiles EAS, tema Titanium Glass, navegación, componentes, pruebas y documentación quedaron implementados. El detalle y sus límites están registrados en [`PHASE_1.md`](./PHASE_1.md).

La generación OpenAPI, el cliente HTTP y la autenticación segura se completaron en Fase 2. El job móvil de CI permanece para la fase de automatización correspondiente; el detalle está en [`PHASE_2.md`](./PHASE_2.md).

### Fase 3 móvil — completada

Home, catálogo, búsqueda, filtros, detalle y favoritos consumen la API. El DTO público fue separado del administrativo y V36 agregó favoritos con RLS. El detalle y la evidencia están en [`PHASE_3.md`](./PHASE_3.md).

## 12. Criterios para continuar

No avanzar una fase dejando el baseline rojo. Cada fase debe registrar:

- comandos ejecutados y resultados;
- archivos modificados;
- migraciones nuevas;
- riesgos cerrados y pendientes;
- compatibilidad backend/web/móvil;
- ausencia de secretos en Git, bundle, APK/AAB/IPA y logs.
