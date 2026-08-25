# FASE 03 — Backend + API

ESTADO: COMPLETED

## Resultado

- API REST real inventariada y versionada: 98 rutas, 120 operaciones.
- Contrato OpenAPI 3.1 portable con identificadores únicos y seguridad explícita.
- La Web y Flutter quedan sobre el mismo backend y la misma lógica de negocio.
- `spring.jpa.open-in-view=false` evita cargas de datos implícitas desde la capa web.
- Upload de imagen endurecido con validación de firma JPEG/PNG/GIF/WebP y creación sin sobrescritura.
- Rutas públicas documentadas sin bearer; el resto hereda autenticación JWT.
- Exportación y validación del contrato automatizadas.

## Criterios de aceptación

- [x] Endpoints y parámetros inventariados.
- [x] Validaciones críticas conservadas en backend.
- [x] Autenticación y autorización representadas en el contrato.
- [x] Paginación, uploads, descargas y errores documentados.
- [x] API versionada y utilizable por Web y Flutter.
- [x] Tests unitarios e integración aprobados.
- [x] Compatibilidad Web verificada.
- [x] Sin pendientes críticos de fase.

## Pruebas

- Backend unitarias: 318 aprobadas, 0 fallos.
- Backend integración: 37 aprobadas, 0 fallos; 42 migraciones Flyway aplicadas.
- Validador OpenAPI: 98 rutas y 120 operaciones válidas.
- Frontend: lint, 12 tests y build de producción aprobados.

## Nota de toolchain

El proyecto exige Java 21. Localmente se validó con JDK 22 compatible; CI fija Temurin 21 y ejecuta `mvn clean verify`. El `clean` local no se fuerza mientras el backend del usuario mantiene abierto el JAR, evitando interrumpir el servicio; se ejecutaron por separado suites unitarias e integración sobre código compilado.
