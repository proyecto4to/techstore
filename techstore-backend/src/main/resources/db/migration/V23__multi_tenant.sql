-- Multi-empresa (multi-tenant).
--
-- Es el cambio que no se puede postergar: retrofitear el aislamiento con datos
-- de clientes reales adentro obliga a migrar cada tabla y auditar cada consulta
-- del sistema una por una.
--
-- Estrategia: columna discriminadora. Una sola base y un solo esquema, con
-- tenant_id en cada tabla de negocio. Frente a "una base por empresa" pierde
-- aislamiento físico, pero gana lo que importa a esta escala: una sola conexión,
-- una sola corrida de migraciones y respaldos que no se multiplican.
--
-- Todo lo que ya existe queda en el tenant 1, así que el sistema actual sigue
-- funcionando exactamente igual.

CREATE TABLE tenants (
    id          BIGSERIAL PRIMARY KEY,
    codigo      VARCHAR(50)  NOT NULL UNIQUE,
    nombre      VARCHAR(255) NOT NULL,
    activo      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE tenants IS
    'Empresas que usan el sistema. El tenant 1 es el negocio original, anterior a que esto fuera multi-empresa.';
COMMENT ON COLUMN tenants.codigo IS
    'Identificador estable para URLs y subdominios. No cambia aunque cambie la razón social.';

INSERT INTO tenants (id, codigo, nombre) VALUES (1, 'techstore', 'TechStore');
-- La secuencia arranca en 2: el 1 se insertó a mano.
SELECT setval('tenants_id_seq', 1, true);

-- ---------------------------------------------------------------------------
-- La columna en cada tabla de negocio.
--
-- Quedan afuera a propósito:
--   roles          catálogo compartido: Admin/Cajero/Cliente significan lo mismo
--                  para todas las empresas.
--   intento_login  se escribe ANTES de autenticar, cuando todavía no se sabe de
--                  qué empresa es quien intenta entrar.
-- ---------------------------------------------------------------------------
ALTER TABLE usuarios              ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 REFERENCES tenants(id);
ALTER TABLE clientes              ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 REFERENCES tenants(id);
ALTER TABLE proveedores           ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 REFERENCES tenants(id);
ALTER TABLE marcas                ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 REFERENCES tenants(id);
ALTER TABLE modelos               ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 REFERENCES tenants(id);
ALTER TABLE productos             ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 REFERENCES tenants(id);
ALTER TABLE pedidos               ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 REFERENCES tenants(id);
ALTER TABLE pedido_detalles       ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 REFERENCES tenants(id);
ALTER TABLE facturas              ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 REFERENCES tenants(id);
ALTER TABLE pagos                 ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 REFERENCES tenants(id);
ALTER TABLE notas_credito         ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 REFERENCES tenants(id);
ALTER TABLE nota_credito_detalles ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 REFERENCES tenants(id);
ALTER TABLE movimiento_caja       ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 REFERENCES tenants(id);
ALTER TABLE cierres_caja          ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 REFERENCES tenants(id);
ALTER TABLE aperturas_caja        ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 REFERENCES tenants(id);
ALTER TABLE correos               ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 REFERENCES tenants(id);
ALTER TABLE audit_log             ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 REFERENCES tenants(id);
ALTER TABLE autonumerador         ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 REFERENCES tenants(id);
ALTER TABLE configuracion         ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 REFERENCES tenants(id);

-- ---------------------------------------------------------------------------
-- Las restricciones únicas pasan a ser por empresa.
--
-- Sin esto, que una empresa emita su factura 001-001-0000001 le impediría a
-- todas las demás emitir la suya. Cada negocio numera su propia serie.
-- ---------------------------------------------------------------------------
ALTER TABLE facturas        DROP CONSTRAINT facturas_numero_key;
ALTER TABLE facturas        ADD  CONSTRAINT uk_factura_tenant_numero      UNIQUE (tenant_id, numero);

ALTER TABLE notas_credito   DROP CONSTRAINT notas_credito_numero_key;
ALTER TABLE notas_credito   ADD  CONSTRAINT uk_nc_tenant_numero           UNIQUE (tenant_id, numero);

ALTER TABLE pedidos         DROP CONSTRAINT pedidos_numero_key;
ALTER TABLE pedidos         ADD  CONSTRAINT uk_pedido_tenant_numero       UNIQUE (tenant_id, numero);

ALTER TABLE pagos           DROP CONSTRAINT pagos_numero_key;
ALTER TABLE pagos           ADD  CONSTRAINT uk_pago_tenant_numero         UNIQUE (tenant_id, numero);

ALTER TABLE movimiento_caja DROP CONSTRAINT movimiento_caja_numero_key;
ALTER TABLE movimiento_caja ADD  CONSTRAINT uk_movimiento_tenant_numero   UNIQUE (tenant_id, numero);

ALTER TABLE cierres_caja    DROP CONSTRAINT cierres_caja_numero_key;
ALTER TABLE cierres_caja    ADD  CONSTRAINT uk_cierre_tenant_numero       UNIQUE (tenant_id, numero);

ALTER TABLE aperturas_caja  DROP CONSTRAINT aperturas_caja_numero_key;
ALTER TABLE aperturas_caja  ADD  CONSTRAINT uk_apertura_tenant_numero     UNIQUE (tenant_id, numero);

-- El autonumerador es el que reparte esos números: si no fuera por empresa,
-- todas compartirían el mismo contador y se pisarían las series.
ALTER TABLE autonumerador   DROP CONSTRAINT autonumerador_entidad_key;
ALTER TABLE autonumerador   ADD  CONSTRAINT uk_autonumerador_tenant       UNIQUE (tenant_id, entidad);

ALTER TABLE marcas          DROP CONSTRAINT marcas_nombre_key;
ALTER TABLE marcas          ADD  CONSTRAINT uk_marca_tenant_nombre        UNIQUE (tenant_id, nombre);

ALTER TABLE proveedores     DROP CONSTRAINT proveedores_ruc_key;
ALTER TABLE proveedores     ADD  CONSTRAINT uk_proveedor_tenant_ruc       UNIQUE (tenant_id, ruc);

ALTER TABLE clientes        DROP CONSTRAINT clientes_cedula_key;
ALTER TABLE clientes        ADD  CONSTRAINT uk_cliente_tenant_cedula      UNIQUE (tenant_id, cedula);

-- configuracion tenía la clave primaria sobre 'clave', así que hay que rehacerla:
-- dos empresas necesitan poder guardar su propio 'empresa.ruc'.
ALTER TABLE configuracion   DROP CONSTRAINT configuracion_pkey;
ALTER TABLE configuracion   ADD  CONSTRAINT configuracion_pkey PRIMARY KEY (tenant_id, clave);

-- usuarios.email queda GLOBAL a propósito: el login es único en todo el sistema.
-- Primero se autentica la persona y recién después se resuelve a qué empresa
-- pertenece. Si el email fuera por empresa, habría que preguntar "¿de qué
-- empresa sos?" antes de pedir la contraseña.

-- ---------------------------------------------------------------------------
-- Índices: toda consulta va a filtrar por tenant, así que va primero en el
-- índice. Los que ya son compuestos con tenant_id adelante no necesitan otro.
-- ---------------------------------------------------------------------------
CREATE INDEX idx_productos_tenant       ON productos(tenant_id);
CREATE INDEX idx_pedidos_tenant         ON pedidos(tenant_id);
CREATE INDEX idx_facturas_tenant        ON facturas(tenant_id);
CREATE INDEX idx_movimiento_caja_tenant ON movimiento_caja(tenant_id);
CREATE INDEX idx_usuarios_tenant        ON usuarios(tenant_id);
CREATE INDEX idx_audit_log_tenant       ON audit_log(tenant_id, fecha DESC);
CREATE INDEX idx_correos_tenant         ON correos(tenant_id);
CREATE INDEX idx_clientes_tenant        ON clientes(tenant_id);
