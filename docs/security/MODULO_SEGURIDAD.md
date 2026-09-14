# Módulo de Seguridad

Usuarios, roles, módulos, acciones y permisos de TechStore: cómo estaba el
sistema, qué se agregó y cómo se usa.

Última actualización: 2026-09-13.

---

## 1. Cómo estaba antes

TechStore ya tenía autenticación y autorización funcionando, y nada de eso se
reemplazó.

| Pieza | Dónde vivía | Qué hacía |
|---|---|---|
| Autenticación | `usuarios/AuthService`, `JwtService`, `JwtAuthenticationFilter` | Login con JWT de acceso y refresco, sesión sin estado |
| Rol del usuario | `usuarios.rol_id`, un rol por persona | Admin, Cajero, Cliente y Repartidor |
| Autorización por código | `@PreAuthorize("hasRole('X')")` en cada controlador | El piso: qué rol puede llamar a qué endpoint |
| Autorización por datos | `permisos_rol` y `acceso_pantalla_rol` (migración V48) | Matriz rol × módulo × acción, y qué pantallas ve cada rol |
| Aplicación de la matriz | `seguridad/PermisoInterceptor` | Traduce prefijo de ruta a módulo y verbo HTTP a acción |
| Auditoría | `audit_log`, `auditoria/AuditoriaService` | Quién hizo qué, con valores antes y después |

La regla de convivencia entre las dos capas ya estaba definida y se mantuvo: **la
matriz solo puede restringir**. Las anotaciones son el piso; destildar una
casilla cierra, tildarla no abre lo que el código niega.

### Qué faltaba

- Permisos por persona. Si a un cajero le faltaba una acción, había que abrirla
  para todos los cajeros o inventar un rol.
- Una vista de permisos efectivos que dijera de dónde sale cada permiso.
- Alta y baja de roles, y un catálogo consultable de módulos y acciones.
- Que la Web y el APK usaran la matriz. Los dos decidían menús y rutas con el
  nombre del rol escrito en el código, así que configurar un permiso no cambiaba
  nada de lo que la gente veía.
- Auditoría de los intentos rechazados, y desde qué plataforma se hizo cada cosa.
- Protección del último administrador.

---

## 2. Arquitectura

```text
                    PostgreSQL
        permisos_rol · permisos_usuario · acceso_pantalla_rol
                         │
                   PermisoService
         (una sola resolución para todo el sistema)
                         │
        ┌────────────────┴────────────────┐
        │                                 │
  PermisoInterceptor                GET /auth/mis-permisos
  (rechaza el pedido)               (le dice al cliente qué dibujar)
        │                                 │
   toda la API                    ┌───────┴───────┐
                                  │               │
                              Web React      APK Flutter
```

Los dos clientes preguntan lo mismo al mismo servidor. No hay una segunda tabla
de reglas escrita en el frontend ni en la app: lo que muestran sale de
`/api/v1/auth/mis-permisos`, y lo que de verdad restringe es el backend en cada
llamada.

---

## 3. El modelo

```text
Usuario ──> Rol ──> Permisos ──> Módulo ──> Acción
   │
   └──> Excepciones individuales (conceden o restringen)
```

**Usuario.** Sigue siendo la tabla de siempre, con su rol único. No se le agregó
ninguna columna.

**Rol.** Conjunto reutilizable de permisos. Se le agregó `activo` para poder
darlo de baja sin borrarlo.

**Módulo.** Un área del negocio tal como la nombra quien administra: Productos,
Caja, Facturas, Seguridad. Son las constantes del enum `Modulo`, y cada una
gobierna un conjunto de prefijos de la API.

**Acción.** Ver, Crear, Editar y Eliminar, en el enum `AccionPermiso`. Se deducen
del verbo HTTP, con dos precisiones que ya existían y se conservan: un POST que
en realidad consulta cuenta como Ver, y escribir sobre algo colgado de un
elemento concreto cuenta como Editar ese elemento.

**Permiso.** Una casilla: rol o persona, más módulo, más acción, más permitido.

---

## 4. Permisos efectivos

Esta es la única regla de resolución, y vale igual para todos los módulos:

```text
1. La anotación del backend        (si niega, se terminó la discusión)
2. Restricción individual          (permitido = false: gana sobre todo lo demás)
3. Concesión individual            (permitido = true)
4. La matriz del rol
5. Sin nada configurado            (queda permitido)
```

Gana lo que niega. Una restricción puesta a una persona es una decisión tomada
sobre esa persona y el rol no la deshace.

El punto 5 conserva el comportamiento anterior a la migración V48: el sistema
funcionaba sin estas tablas y su ausencia no puede dejar a nadie afuera. La
excepción son los roles nuevos, que nacen con toda la matriz en falso, porque no
tienen nada previo que preservar y darles todo sería lo contrario del mínimo
privilegio.

### Origen

Cada acción en la respuesta de permisos efectivos viene con su origen, para que
no haya ambigüedad sobre por qué alguien puede algo:

| Origen | Qué significa |
|---|---|
| `ROL` | Heredado del rol |
| `CONCEDIDO` | Asignado individualmente |
| `RESTRINGIDO` | Restringido individualmente |
| `PREDETERMINADO` | Nadie lo configuró; queda permitido por omisión |

Una concesión individual no supera el piso del backend. Si se concede algo que
la anotación del controlador niega, el botón aparece y la operación devuelve 403.
Es la misma propiedad que ya tenía la matriz por rol.

---

## 5. Base de datos

Migración `V56__seguridad_permisos_por_usuario.sql`. Es aditiva: no borra, no
reasigna y no cambia ningún dato existente.

| Cambio | Detalle |
|---|---|
| `permisos_usuario` (tabla nueva) | tenant, usuario, módulo, acción, permitido y motivo, con unicidad por combinación |
| RLS en `permisos_usuario` | Mismo patrón que V24 y V48: política de aislamiento por empresa más la de mantenimiento para Flyway |
| `roles.activo` | `NOT NULL DEFAULT TRUE`, así ningún rol cambia de estado al migrar |
| `audit_log.resultado` | `Permitido` o `Rechazado`; nulo en todo lo anterior |
| `audit_log.plataforma` | `Web`, `Movil` o `Api`; nulo en todo lo anterior |
| Índices | Consulta de permisos por persona y consulta de rechazos por fecha |

---

## 6. Protección del backend

Ocultar el menú no protege nada. Hay tres capas y un pedido tiene que pasar las
tres:

1. **Spring Security.** Sin token válido no se entra a nada que no sea público.
2. **`PermisoInterceptor`.** Antes de llegar al controlador resuelve el permiso
   efectivo de la persona sobre el módulo de esa ruta. Si no lo tiene, corta con
   403 y deja el intento auditado.
3. **`@PreAuthorize` del controlador.** El piso de siempre.

### El módulo de Seguridad

`SeguridadController` lleva `@PreAuthorize("hasRole('Admin')")` **en la clase**, no
en cada método, para que agregar un endpoint no pueda olvidarse de cerrarlo. Lo
mismo `UsuarioController` y `AuditoriaController`.

La excepción deliberada es `GET /api/v1/auth/mis-permisos`, que responde a
cualquier sesión. Preguntar qué puedo hacer yo no es administrar seguridad: lo
necesita cualquiera para dibujar su propio menú.

---

## 7. Endpoints

Todos bajo `/api/v1`. Salvo el último, todos exigen rol Administrador.

| Método y ruta | Para qué |
|---|---|
| `GET /seguridad/catalogo` | Módulos, acciones, pantallas y qué ruta gobierna cada módulo |
| `GET /seguridad/roles` | Nombres de los roles |
| `GET /seguridad/roles/detalle` | Roles con descripción, estado y cuánta gente los tiene |
| `POST /seguridad/roles` | Crear un rol, sin permisos |
| `PUT /seguridad/roles/{rol}` | Cambiar descripción, activar o desactivar |
| `GET /seguridad/roles/{rol}/usuarios` | Quiénes tienen ese rol |
| `GET /seguridad/roles/{rol}/permisos` | La matriz del rol y sus pantallas |
| `PUT /seguridad/roles/{rol}/permisos` | Cambiar una casilla de la matriz |
| `PUT /seguridad/roles/{rol}/pantallas` | Mostrar u ocultar una pantalla |
| `GET /seguridad/usuarios/{id}/permisos` | Permisos efectivos con su origen |
| `PUT /seguridad/usuarios/{id}/permisos` | Conceder o restringir una acción |
| `DELETE /seguridad/usuarios/{id}/permisos/{modulo}/{accion}` | Quitar la excepción y volver a heredar |
| `GET /usuarios?q=&rol=&activo=` | Listar con búsqueda y filtros aplicados en la base |
| `PUT /usuarios/{id}/rol` | Cambiar el rol de una persona |
| `PUT /usuarios/{id}/activo` | Activar o desactivar |
| `GET /auditoria?resultado=&plataforma=` | Auditoría, ahora también por resultado y plataforma |
| `GET /auth/mis-permisos` | Los permisos de quien pregunta. Cualquier sesión |

---

## 8. Web

- `usePermisos` pide los permisos una vez por sesión y los comparte con toda la
  aplicación. Mientras no llegó la respuesta, `puede()` devuelve falso: antes que
  ofrecer una acción que va a rebotar, no se ofrece.
- `ProtectedRoute` acepta `modulo` y `accion` además del `rol` de siempre, que
  sigue funcionando igual. Mientras carga muestra que está comprobando, en vez de
  rebotar al inicio.
- `AdminTabs` y el menú esconden lo que la persona no puede usar.
- `AdminSeguridadPage` es la pantalla nueva, con cinco pestañas: Usuarios, Roles,
  Permisos, Módulos y Auditoría.
- Se muestran nombres, no identificadores: «Clientes», «Ver», «Heredado del rol».

## 9. APK

- `permisosProvider` hace lo mismo que el hook de la Web, con Riverpod.
- `app_router` agrega la comprobación por módulo encima de la de rol. Mientras no
  llegaron los permisos no decide nada, para no dejar la app trabada en el inicio.
- El inicio y el hub de administración esconden las baldosas de trabajo que la
  persona no puede usar. El catálogo, el carrito y la cuenta propia nunca se
  bloquean: son el uso normal de la tienda, y un permiso mal puesto no puede
  dejar a un cliente sin poder comprar.
- La pantalla de Seguridad tiene dos pestañas: «Por rol», que ya existía, y «Por
  persona», que es nueva.

---

## 10. Auditoría

`AuditoriaService` ahora guarda en qué terminó el intento y desde dónde se hizo.
Las llamadas que ya existían no cambiaron: auditan después de completar la
operación, así que quedan como `Permitido`. La plataforma se deduce del pedido en
curso, primero por la cabecera `X-Plataforma` que mandan los clientes propios y,
si no viene, por el `User-Agent`.

Lo que se audita del módulo de Seguridad: cambios en la matriz de un rol, cambios
de pantallas, concesiones y restricciones individuales, alta y edición de roles,
cambio de rol de una persona, activación y desactivación, y **cada intento
rechazado por el interceptor**.

Un ejemplo de rechazo, tal como se lee en la pantalla:

```text
juan@techstore.py · 13/09/2026 18:43 · Eliminar · Rechazado · Movil
DELETE /api/v1/productos/145: sin permiso de eliminar en Productos
```

---

## 11. Protecciones

Quedarse sin administradores es lo único que no tiene arreglo desde la interfaz:
habría que entrar a la base de datos a mano. Por eso se comprueba antes y se
rechaza.

`AdministradoresGuard` define «administrador con mando» como alguien que tiene el
rol, está activo y no tiene una restricción individual sobre Seguridad. Con eso:

| Operación | Resultado |
|---|---|
| Desactivar al último administrador con mando | Rechazada |
| Cambiarle el rol al último administrador con mando | Rechazada |
| Bajarse uno mismo de administrador | Rechazada |
| Restringirse uno mismo el acceso a Seguridad | Rechazada |
| Restringirle Seguridad al último administrador | Rechazada |
| Quitarle Seguridad al rol Administrador | Rechazada |
| Desactivar el rol Administrador | Rechazada |
| Desactivar un rol que todavía tiene gente activa | Rechazada, con el número de personas |

El nombre del rol administrador vive en un solo lugar, `RolesDelSistema`, en vez
de repetido por los servicios.

---

## 12. Cómo se hacen las cosas

### Agregar un módulo

1. Sumar la constante al enum `Modulo` con su etiqueta en castellano.
2. Mapear sus prefijos de ruta en `PermisoInterceptor.rutas()`.
3. Nada más. El catálogo, la matriz, la Web y el APK lo muestran solos, porque
   los tres se dibujan con lo que responde el backend.

Los roles existentes no lo tendrán cargado, así que por omisión queda permitido y
gobernado por la anotación del controlador, que es como venía funcionando. Si el
módulo tiene que nacer cerrado, se destilda desde la pantalla.

### Agregar una acción

Sumarla al enum `AccionPermiso` y decidir en `AccionPermiso.deMetodo` o en
`PermisoInterceptor.accionDe` qué pedidos la representan. Aparece sola en las dos
interfaces.

### Crear un rol

Seguridad → Roles → Crear. Nace sin ningún permiso. Después se le habilita lo que
necesita desde la matriz y se le asigna gente desde Usuarios.

### Configurar permisos

- **De un rol:** Seguridad → Permisos, se elige el rol y se tildan las casillas.
  Le cambia el trabajo a todas las personas que lo tengan; la pantalla dice
  cuántas son.
- **De una persona:** Seguridad → Usuarios → Permisos. Conviene escribir el
  motivo: dentro de seis meses, un «no» sin explicación es indistinguible de un
  error de tipeo. Para volver atrás se quita la excepción y vuelve a valer el rol.

---

## 13. Archivos

### Creados

```text
techstore-backend/
  src/main/resources/db/migration/V56__seguridad_permisos_por_usuario.sql
  src/main/java/com/techstore/seguridad/
    PermisoUsuario.java            PermisoUsuarioRepository.java
    PermisosEfectivosResponse.java CatalogoSeguridadResponse.java
    OrigenPermiso.java             RolesDelSistema.java
    RolAdminService.java           RolDetalleResponse.java
    AdministradoresGuard.java
  src/main/java/com/techstore/auditoria/Plataforma.java
  src/main/java/com/techstore/usuarios/AsignarRolRequest.java
  src/main/java/com/techstore/usuarios/UsuarioSpecs.java
  src/test/java/com/techstore/integracion/SeguridadPorUsuarioIT.java

techstore-frontend/src/
  hooks/usePermisos.js             hooks/usePermisos.test.jsx
  pages/AdminSeguridadPage.jsx     services/seguridadService.js

techstore-mobile/
  lib/features/security/domain/app_permissions.dart
  lib/features/security/data/security_repository.dart
  lib/features/security/application/security_providers.dart
  test/features/security/permisos_test.dart

docs/security/MODULO_SEGURIDAD.md
```

### Modificados

```text
techstore-backend/  PermisoService, PermisoInterceptor, SeguridadController,
                    AuthController, UsuarioController, UsuarioService,
                    UsuarioRepository, Rol, RolRepository,
                    AccesoPantallaRolRepository, AuditLog, AuditoriaService,
                    AuditoriaResponse, AuditLogSpecs, AuditoriaController,
                    messages.properties, messages_en.properties,
                    UsuarioServiceTest, MigracionesIT

techstore-frontend/ App.jsx, ProtectedRoute, AdminTabs, AdminUsuariosPage,
                    api.js, usuarioService, auditoriaService

techstore-mobile/   app_router, home_page, business_hub_page, security_page,
                    resource_definition, api_client
```

---

## 14. Pruebas

Corridas el 2026-09-13 sobre el código de esta entrega.

| Suite | Resultado |
|---|---|
| Backend unitarias | 367 pasadas |
| Backend integración | 95 pasadas |
| Web, lint | Sin avisos |
| Web, vitest | 54 pasadas en 11 archivos |
| Flutter, analyze | Sin observaciones |
| Flutter, test | 175 pasadas, 2 omitidas de forma declarada |

`SeguridadPorUsuarioIT` cubre, contra un PostgreSQL real y con las migraciones
aplicadas de verdad:

1. Un cajero no toca ningún endpoint de administración de Seguridad.
2. Sin sesión tampoco, aunque se conozca la ruta.
3. Cualquiera puede consultar sus propios permisos.
4. Una restricción individual cierra el endpoint aunque el rol lo permita.
5. Una concesión individual no abre lo que el backend niega por código.
6. Los permisos efectivos traen el origen de cada uno.
7. Nadie puede quitarse a sí mismo el acceso a Seguridad.
8. No se puede dejar al sistema sin ningún administrador con mando.
9. Al rol Administrador no se le quita el módulo de Seguridad.
10. Un rol nuevo nace sin ningún permiso.
11. El catálogo se arma solo desde el backend.
12. El intento rechazado queda auditado, con su plataforma.

`MigracionesIT` comprobaba una cantidad de migraciones que ya estaba
desactualizada en dos antes de esta entrega: esperaba 53 cuando había 55. Se
actualizó a 56.

---

## 15. Compatibilidad

- Ningún usuario cambió de rol, de estado ni de permisos.
- Ningún permiso existente se borró ni se modificó.
- Las rutas y los roles de la Web siguen funcionando como antes; la comprobación
  por módulo se suma, no reemplaza.
- La app mantiene su navegación por rol y le agrega la de permisos.
- El único cambio de comportamiento visible es deliberado y es el pedido: quien
  no tenga un permiso deja de ver esa opción, y si igual llama a la API, el
  backend la rechaza.
