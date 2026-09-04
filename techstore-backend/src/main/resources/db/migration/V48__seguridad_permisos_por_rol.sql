-- Permisos y acceso a pantallas por rol, parametrizables.
--
-- Hasta acá los permisos vivían escritos en las anotaciones de cada
-- controlador: funcionaban, pero cambiarlos exigía tocar código y recompilar.
--
-- Estas dos tablas los vuelven datos. La regla de convivencia con lo que ya
-- existe es deliberada y va también en el servicio: **estos permisos solo
-- pueden restringir, nunca ampliar**. Las anotaciones siguen siendo el piso, y
-- lo que se marque acá se aplica encima. Así, habilitar algo por pantalla no
-- puede abrir un endpoint que el código considera cerrado.

CREATE TABLE permisos_rol (
    id         BIGSERIAL PRIMARY KEY,
    tenant_id  BIGINT NOT NULL REFERENCES tenants(id),
    rol_id     BIGINT NOT NULL REFERENCES roles(id),
    modulo     VARCHAR(40) NOT NULL,
    accion     VARCHAR(20) NOT NULL,
    permitido  BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT permisos_rol_unico UNIQUE (tenant_id, rol_id, modulo, accion)
);

COMMENT ON TABLE permisos_rol IS
    'Qué puede hacer cada rol en cada módulo. Solo restringe: nunca amplía lo que las anotaciones del backend ya niegan.';

CREATE INDEX idx_permisos_rol_consulta ON permisos_rol (tenant_id, rol_id, modulo);

CREATE TABLE acceso_pantalla_rol (
    id         BIGSERIAL PRIMARY KEY,
    tenant_id  BIGINT NOT NULL REFERENCES tenants(id),
    rol_id     BIGINT NOT NULL REFERENCES roles(id),
    pantalla   VARCHAR(60) NOT NULL,
    permitido  BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT acceso_pantalla_rol_unico UNIQUE (tenant_id, rol_id, pantalla)
);

COMMENT ON TABLE acceso_pantalla_rol IS
    'Qué pantallas ve cada rol. Ocultar una pantalla no es seguridad: el permiso del módulo es el que manda en la API.';

CREATE INDEX idx_acceso_pantalla_consulta ON acceso_pantalla_rol (tenant_id, rol_id);

-- Aislamiento por empresa, mismo patrón que V24.
ALTER TABLE permisos_rol ENABLE ROW LEVEL SECURITY;
ALTER TABLE permisos_rol FORCE ROW LEVEL SECURITY;
ALTER TABLE acceso_pantalla_rol ENABLE ROW LEVEL SECURITY;
ALTER TABLE acceso_pantalla_rol FORCE ROW LEVEL SECURITY;

CREATE POLICY aislamiento_tenant ON permisos_rol
    USING (tenant_id = tenant_actual())
    WITH CHECK (tenant_id = tenant_actual());

CREATE POLICY aislamiento_tenant ON acceso_pantalla_rol
    USING (tenant_id = tenant_actual())
    WITH CHECK (tenant_id = tenant_actual());

DO $$
BEGIN
    EXECUTE format($f$
        CREATE POLICY mantenimiento ON permisos_rol
            TO %I
            USING (tenant_actual() IS NULL)
            WITH CHECK (tenant_actual() IS NULL)
    $f$, current_user);
    EXECUTE format($f$
        CREATE POLICY mantenimiento ON acceso_pantalla_rol
            TO %I
            USING (tenant_actual() IS NULL)
            WITH CHECK (tenant_actual() IS NULL)
    $f$, current_user);
END $$;

-- ---------------------------------------------------------------------------
-- Siembra: exactamente lo que el sistema permite hoy.
--
-- La idea es que activar esta capa no cambie el comportamiento de nadie el
-- primer día. Lo que sigue sale de recorrer los controladores y anotar qué rol
-- llega a qué; a partir de acá se edita desde la pantalla de Seguridad.
-- ---------------------------------------------------------------------------

-- El administrador puede todo, en todos los módulos.
INSERT INTO permisos_rol (tenant_id, rol_id, modulo, accion, permitido)
SELECT t.id, r.id, m.modulo, a.accion, TRUE
FROM tenants t
CROSS JOIN roles r
CROSS JOIN (VALUES
    ('PRODUCTOS'), ('PEDIDOS'), ('COMPRAS'), ('PROVEEDORES'), ('CAJA'),
    ('FACTURAS'), ('USUARIOS'), ('EMPRESA'), ('ENVIOS'), ('REPARTOS'),
    ('VEHICULOS'), ('AUDITORIA'), ('CONTABILIDAD'), ('CONFIGURACION'),
    ('CONVERSACIONES'), ('REPORTES'), ('SEGURIDAD')
) AS m(modulo)
CROSS JOIN (VALUES ('VER'), ('CREAR'), ('EDITAR'), ('ELIMINAR')) AS a(accion)
WHERE r.nombre = 'Admin'
ON CONFLICT DO NOTHING;

-- Los demás roles arrancan sin nada y se les habilita lo que hoy usan.
INSERT INTO permisos_rol (tenant_id, rol_id, modulo, accion, permitido)
SELECT t.id, r.id, m.modulo, a.accion, FALSE
FROM tenants t
CROSS JOIN roles r
CROSS JOIN (VALUES
    ('PRODUCTOS'), ('PEDIDOS'), ('COMPRAS'), ('PROVEEDORES'), ('CAJA'),
    ('FACTURAS'), ('USUARIOS'), ('EMPRESA'), ('ENVIOS'), ('REPARTOS'),
    ('VEHICULOS'), ('AUDITORIA'), ('CONTABILIDAD'), ('CONFIGURACION'),
    ('CONVERSACIONES'), ('REPORTES'), ('SEGURIDAD')
) AS m(modulo)
CROSS JOIN (VALUES ('VER'), ('CREAR'), ('EDITAR'), ('ELIMINAR')) AS a(accion)
WHERE r.nombre <> 'Admin'
ON CONFLICT DO NOTHING;

-- Cajero: mostrador, caja, compras y proveedores.
UPDATE permisos_rol p SET permitido = TRUE, updated_at = CURRENT_TIMESTAMP
FROM roles r
WHERE p.rol_id = r.id AND r.nombre = 'Cajero'
  AND (p.modulo, p.accion) IN (
      ('PRODUCTOS','VER'),
      ('PEDIDOS','VER'), ('PEDIDOS','EDITAR'),
      ('COMPRAS','VER'), ('COMPRAS','CREAR'), ('COMPRAS','EDITAR'),
      ('PROVEEDORES','VER'), ('PROVEEDORES','CREAR'), ('PROVEEDORES','EDITAR'),
      ('CAJA','VER'), ('CAJA','CREAR'),
      ('FACTURAS','VER'), ('FACTURAS','CREAR'),
      ('ENVIOS','EDITAR'),
      -- El repartidor le entrega al cajero el efectivo que cobro, y es el cajero
      -- quien acepta esa transferencia: repartos tambien es su mostrador.
      ('REPARTOS','VER'), ('REPARTOS','EDITAR'),
      ('REPORTES','VER'),
      ('CONVERSACIONES','VER'), ('CONVERSACIONES','EDITAR')
  );

-- Repartidor: sus entregas y su vehículo.
UPDATE permisos_rol p SET permitido = TRUE, updated_at = CURRENT_TIMESTAMP
FROM roles r
WHERE p.rol_id = r.id AND r.nombre = 'Repartidor'
  AND (p.modulo, p.accion) IN (
      -- CREAR en repartos y vehiculos no es administrar: es registrar el cobro
      -- de una entrega, vaciar su caja y tomar el vehiculo del turno. Son las
      -- acciones de su propio trabajo.
      ('REPARTOS','VER'), ('REPARTOS','CREAR'), ('REPARTOS','EDITAR'),
      ('VEHICULOS','VER'), ('VEHICULOS','CREAR'), ('VEHICULOS','EDITAR'),
      ('PEDIDOS','VER'),
      ('CONVERSACIONES','VER'), ('CONVERSACIONES','EDITAR')
  );

-- Cliente: comprar y seguir lo suyo.
UPDATE permisos_rol p SET permitido = TRUE, updated_at = CURRENT_TIMESTAMP
FROM roles r
WHERE p.rol_id = r.id AND r.nombre = 'Cliente'
  AND (p.modulo, p.accion) IN (
      ('PRODUCTOS','VER'),
      ('PEDIDOS','VER'), ('PEDIDOS','CREAR'),
      ('FACTURAS','VER'),
      -- VER envios: cotizar durante la compra y seguir el propio pedido. La
      -- configuracion de tarifas sigue cerrada por la anotacion del backend,
      -- que esta capa no puede abrir.
      ('ENVIOS','VER'),
      -- EDITAR porque marcar leida la propia conversacion viaja como PUT:
      -- es parte de leer, no de administrar conversaciones ajenas.
      ('CONVERSACIONES','VER'), ('CONVERSACIONES','CREAR'),
      ('CONVERSACIONES','EDITAR')
  );

-- ---------------------------------------------------------------------------
-- Pantallas: las de la aplicación móvil, con el acceso que tienen hoy.
-- ---------------------------------------------------------------------------
INSERT INTO acceso_pantalla_rol (tenant_id, rol_id, pantalla, permitido)
SELECT t.id, r.id, p.pantalla,
       CASE
           WHEN r.nombre = 'Admin' THEN TRUE
           WHEN r.nombre = 'Cajero' THEN p.pantalla IN (
               'catalogo','carrito','pedidos','facturas','notificaciones','chat',
               'cuenta','caja','ventas','compras','operaciones')
           WHEN r.nombre = 'Repartidor' THEN p.pantalla IN (
               'notificaciones','chat','cuenta','repartidor')
           ELSE p.pantalla IN (
               'catalogo','carrito','pedidos','facturas','direcciones',
               'favoritos','notificaciones','chat','cuenta')
       END
FROM tenants t
CROSS JOIN roles r
CROSS JOIN (VALUES
    ('catalogo'), ('carrito'), ('pedidos'), ('facturas'), ('direcciones'),
    ('favoritos'), ('notificaciones'), ('chat'), ('cuenta'), ('caja'),
    ('ventas'), ('compras'), ('operaciones'), ('repartidor'), ('administracion'),
    ('seguridad')
) AS p(pantalla)
ON CONFLICT DO NOTHING;
