# TechStore — E-Commerce profesional

Sistema de e-commerce full-stack: **Spring Boot 3.2 (Java 21) + React 18 + PostgreSQL 15**, orquestado con Docker Compose.

## Stack

| Capa | Tecnología |
|---|---|
| Backend | Spring Boot 3.2, Spring Security (JWT), Spring Data JPA, Flyway, MapStruct, Lombok, springdoc-openapi |
| Frontend | React 18, Vite 8.1, Tailwind CSS 3, React Router 7, TanStack Query 5, Zustand, Axios |
| Base de datos | PostgreSQL 15 (migraciones versionadas con Flyway) |
| Infraestructura | Docker Compose (postgres + backend + frontend) |

> 📖 ¿Buscás la guía de uso para el día a día (ventas, caja, devoluciones,
> compras)? Está en **[MANUAL.md](MANUAL.md)**.

## Inicio rápido

### Opción 0 — Doble clic 🖱️

En el escritorio de Windows: **TechStore** levanta PostgreSQL + backend + frontend y abre la tienda;
**TechStore - Detener** apaga todo. (Los scripts viven en la raíz del repo:
`iniciar-techstore.ps1` / `detener-techstore.ps1`.) Este sigue siendo el modo
local predeterminado: conserva el JAR y los accesos directos actuales. El
lanzador exige secretos válidos y el cierre solo detiene PIDs registrados por
TechStore; si otro programa ocupa un puerto, se informa el conflicto sin matarlo.

### Opción A — Docker (requiere Docker Desktop)

```powershell
Copy-Item .env.example .env
# Edita .env y reemplaza todos los placeholders.
docker compose up -d --build
```

El `.env.example` activa el perfil `local` (Vite con recarga). Para una
instalación comercial se parte de `.env.commercial.example`:

```powershell
Copy-Item .env.commercial.example .env.commercial
# Define tres secretos fuertes, distintos cuando corresponda, y el dominio HTTPS.
docker compose --env-file .env.commercial --profile commercial up -d --build
```

El perfil `commercial` compila el frontend y lo sirve con nginx sin privilegios.
PostgreSQL y la API quedan ligados a `127.0.0.1`; un proxy inverso del host debe
publicar **solo** el frontend con HTTPS. La aplicación conecta como
`techstore_app` (sin superusuario ni `BYPASSRLS`), mientras Flyway usa el rol
migrador. Base y archivos subidos persisten en `postgres_data` y `uploads_data`.

### Opción B — Manual con PostgreSQL portable (sin Docker)

El repo incluye herramientas portables en `tools/` (Maven 3.9.9 y PostgreSQL 15).
El *data directory* vive fuera de OneDrive, en `%LOCALAPPDATA%\TechStore\pgdata`.

```powershell
# 1. Arrancar PostgreSQL (una sola vez por sesión)
tools\pgsql\bin\pg_ctl.exe -D "$env:LOCALAPPDATA\TechStore\pgdata" -l "$env:LOCALAPPDATA\TechStore\pg.log" start

# 2. Backend
cd techstore-backend
..\tools\apache-maven-3.9.9\bin\mvn.cmd spring-boot:run
#   (o: java -jar target\techstore-backend-1.0.0.jar)

# 3. Frontend (otra terminal)
cd techstore-frontend
npm run dev

# Detener PostgreSQL al terminar
tools\pgsql\bin\pg_ctl.exe -D "$env:LOCALAPPDATA\TechStore\pgdata" stop
```

### Accesos

| Servicio | URL |
|---|---|
| Frontend | http://localhost:5173 |
| API | http://localhost:8090 |
| Swagger UI | http://localhost:8090/swagger-ui.html |
| Health check | http://localhost:8090/actuator/health |

**Credenciales de prueba:** `admin@techstore.py` / `admin123`

## Funcionalidad implementada

- **Autenticación JWT**: `POST /api/v1/auth/login` (access token 15 min + refresh token 7 días), `POST /api/v1/auth/refresh`, `GET /api/v1/auth/me`. El frontend renueva tokens automáticamente ante un 401.
- **Catálogo de productos**: `GET /api/v1/productos` (paginado, búsqueda por nombre, filtro por marca) y `GET /api/v1/marcas` — públicos. CRUD completo de productos restringido al rol `Admin` (`POST/PUT/DELETE`). La eliminación es baja lógica.
- **Carrito y checkout**: carrito persistente en el navegador (Zustand); `POST /api/v1/pedidos` crea el pedido en estado *Pendiente*, **compromete stock con bloqueo pesimista** (dos checkouts simultáneos no pueden sobrevender) y numera con el autonumerador transaccional. `PUT /{id}/confirmar` (Admin/Cajero) descuenta stock real; `PUT /{id}/cancelar` libera lo comprometido (el dueño anula lo suyo, un Admin cualquiera). Los usuarios ven solo sus pedidos; los Admin, todos.
- **Pagos y facturación**: `POST /api/v1/pedidos/{id}/pago` (Admin/Cajero) cobra un pedido Confirmado y **emite la factura en la misma transacción** — numeración fiscal `001-001-0000001`, IVA incluido desglosado por tasa de cada detalle, estado del pedido pasa a *Pagado*. Doble cobro rechazado. `GET /api/v1/facturas` lista las emitidas. El panel admin del frontend gestiona todo el flujo: Confirmar → Cobrar (con tipo de pago) → factura emitida.
- **Gestión de productos desde la web** (pestaña *Productos* del panel admin): crear, editar, desactivar y reactivar. Un Admin puede listar inactivos con `GET /api/v1/productos?incluirInactivos=true` (el parámetro se ignora para usuarios anónimos o sin rol Admin).
- **Reportes de ventas** (pestaña *Reportes* + `GET /api/v1/reportes/ventas`, Admin/Cajero): total facturado, pedidos por estado y productos más vendidos.
- **Manejo de errores** RFC 7807 (`ProblemDetail`) con mensajes en español y detalle de campos inválidos.
- **Esquema completo** en `V1__init_schema.sql`: usuarios, roles, clientes, proveedores, marcas, modelos, productos, pedidos, pagos, facturas, caja y auditoría — listo para los próximos módulos.
- **Tests unitarios** (JUnit 5 + Mockito): servicios de auth, JWT y productos.

## Estructura

```
techstore/
├── docker-compose.yml
├── techstore-backend/          # API Spring Boot
│   └── src/main/java/com/techstore/
│       ├── config/             # Security, CORS, OpenAPI
│       ├── controller/         # REST endpoints
│       ├── service/            # Lógica de negocio
│       ├── repository/         # Spring Data JPA + Specifications
│       ├── entity/             # Entidades JPA
│       ├── dto/                # Records request/response con validación
│       ├── mapper/             # MapStruct
│       ├── security/           # JWT service + filtro + UserDetails
│       ├── exception/          # Excepciones + handler global
│       └── util/               # PasswordHashGenerator (CLI)
├── techstore-frontend/         # SPA React
│   └── src/
│       ├── pages/              # Catálogo, Login, Admin
│       ├── components/         # Navbar, ProductCard, ProtectedRoute…
│       ├── services/           # Cliente Axios con auto-refresh de tokens
│       ├── store/              # Zustand (sesión persistida)
│       └── utils/              # Formato de moneda (₲, es-PY)
└── tools/                      # Maven portable (no versionado)
```

## Aplicación móvil

La aplicación móvil vive en un repositorio propio: [proyecto4to/techstore-mobile](https://github.com/proyecto4to/techstore-mobile). Ahí están el código Expo, la documentación de sus quince fases y las herramientas de build.

Consume esta API, que sigue siendo la autoridad de permisos, precios, stock, pagos, tenant y fiscalidad. El contrato del que se generan sus tipos es [docs/api/mobile-auth.openapi.yaml](docs/api/mobile-auth.openapi.yaml): si cambia acá, hay que regenerarlos allá.

## Pruebas

```powershell
cd techstore-backend

# Ciclo rápido: solo unitarios (segundos)
..\tools\apache-maven-3.9.9\bin\mvn.cmd test

# Completo: unitarios + integración (levanta un PostgreSQL de verdad)
..\tools\apache-maven-3.9.9\bin\mvn.cmd verify
```

Las de integración (`*IT.java`) levantan **PostgreSQL 15 real** como proceso
hijo, corren las 42 migraciones desde cero sobre una base vacía y ejercitan la
API por HTTP con JWT. Verifican lo que ningún unitario puede: que el esquema y
las entidades no se hayan desincronizado, que la seguridad no deje un endpoint
abierto, y que cobrar descuente stock, mueva la caja y emita factura.

No hace falta Docker. En Windows Home, Docker Desktop exige WSL2, privilegios de
administrador y un reinicio; esto no necesita nada de eso. Si algún día se monta
CI con Docker, migrar a Testcontainers es reescribir `BaseDatosDePrueba` y nada
más: el resto de los tests solo ve una URL JDBC.

## Utilidades

Generar un hash BCrypt (para sembrar usuarios en migraciones):

```bash
cd techstore-backend
mvn -q org.codehaus.mojo:exec-maven-plugin:3.1.0:java \
  -Dexec.mainClass=com.techstore.util.PasswordHashGenerator -Dexec.args=miPassword
```

## Antes de producción

- [ ] Usar `.env.commercial` con el perfil `commercial`; el modo `local` es para desarrollo/uso en esta PC.
- [ ] Reemplazar `JWT_SECRET` y las dos contraseñas de PostgreSQL; la clave runtime debe ser distinta de la administrativa.
- [ ] Cambiar o eliminar inmediatamente todos los usuarios y contraseñas de demostración.
- [ ] Publicar únicamente mediante un proxy HTTPS y limitar `CORS_ALLOWED_ORIGINS` al dominio real; no exponer 5432 ni 8090.
- [ ] Mantener Swagger desactivado (`SPRINGDOC_ENABLED=false`, exigido por el perfil comercial).
- [ ] Automatizar respaldos externos de `postgres_data` y `uploads_data`, y ensayar una restauración antes de operar.
- [ ] Completar revisión legal, fiscal, privacidad, monitoreo y actualizaciones de seguridad.
- [ ] **No usar la salida SIFEN como comprobante legal:** hoy es demostrativa, usa certificado de prueba, no está homologada y no transmite a DNIT.

La lista operativa ampliada está en la sección **Puesta en marcha comercial** de [MANUAL.md](MANUAL.md).

## Roadmap

1. ~~Setup + esquema de BD~~ ✅
2. ~~Autenticación JWT + catálogo de productos~~ ✅
3. ~~Carrito de compras (Zustand) + checkout con control de stock~~ ✅
4. ~~Pagos y facturación con IVA + panel admin de pedidos~~ ✅
5. ~~Panel admin de productos (CRUD desde la web) y reportes de ventas~~ ✅
6. ~~Tests de integración con PostgreSQL real + CI/CD de backend/web/mobile~~ ✅ (sin Docker local)
7. Deploy (AWS / DigitalOcean)
