-- Direcciones de entrega y datos de checkout para la aplicación móvil.
-- La dirección se referencia mientras existe, pero el pedido conserva además
-- una copia de sus datos: editar o borrar la libreta no altera el histórico.

CREATE TABLE direcciones_entrega (
    id                   BIGSERIAL PRIMARY KEY,
    tenant_id            BIGINT       NOT NULL REFERENCES tenants(id),
    usuario_id           BIGINT       NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    nombre_destinatario  VARCHAR(120) NOT NULL,
    telefono             VARCHAR(20)  NOT NULL,
    departamento         VARCHAR(80)  NOT NULL,
    ciudad               VARCHAR(100) NOT NULL,
    barrio               VARCHAR(100),
    direccion_linea_1    VARCHAR(200) NOT NULL,
    direccion_linea_2    VARCHAR(200),
    numero_casa          VARCHAR(40),
    referencia           VARCHAR(300),
    codigo_postal        VARCHAR(20),
    latitud              NUMERIC(10, 7),
    longitud             NUMERIC(10, 7),
    principal            BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ck_direccion_latitud
        CHECK (latitud IS NULL OR latitud BETWEEN -90 AND 90),
    CONSTRAINT ck_direccion_longitud
        CHECK (longitud IS NULL OR longitud BETWEEN -180 AND 180)
);

CREATE INDEX idx_direcciones_usuario
    ON direcciones_entrega (tenant_id, usuario_id, principal DESC, updated_at DESC);

CREATE UNIQUE INDEX uk_direccion_principal_usuario
    ON direcciones_entrega (tenant_id, usuario_id)
    WHERE principal;

ALTER TABLE direcciones_entrega ENABLE ROW LEVEL SECURITY;
ALTER TABLE direcciones_entrega FORCE ROW LEVEL SECURITY;

CREATE POLICY aislamiento_tenant ON direcciones_entrega
    USING (tenant_id = tenant_actual())
    WITH CHECK (tenant_id = tenant_actual());

DO $$
BEGIN
    EXECUTE format($f$
        CREATE POLICY mantenimiento ON direcciones_entrega
            TO %I
            USING (tenant_actual() IS NULL)
            WITH CHECK (tenant_actual() IS NULL)
    $f$, current_user);
END $$;

ALTER TABLE pedidos
    ADD COLUMN direccion_entrega_id         BIGINT REFERENCES direcciones_entrega(id) ON DELETE SET NULL,
    ADD COLUMN direccion_destinatario       VARCHAR(120),
    ADD COLUMN direccion_telefono           VARCHAR(20),
    ADD COLUMN direccion_departamento       VARCHAR(80),
    ADD COLUMN direccion_ciudad             VARCHAR(100),
    ADD COLUMN direccion_barrio             VARCHAR(100),
    ADD COLUMN direccion_linea_1            VARCHAR(200),
    ADD COLUMN direccion_linea_2            VARCHAR(200),
    ADD COLUMN direccion_numero_casa        VARCHAR(40),
    ADD COLUMN direccion_referencia         VARCHAR(300),
    ADD COLUMN direccion_codigo_postal      VARCHAR(20),
    ADD COLUMN direccion_latitud             NUMERIC(10, 7),
    ADD COLUMN direccion_longitud            NUMERIC(10, 7),
    ADD COLUMN metodo_entrega                VARCHAR(40),
    ADD COLUMN metodo_pago_checkout          VARCHAR(40),
    ADD COLUMN idempotency_key               VARCHAR(100),
    ADD COLUMN idempotency_request_hash      VARCHAR(64);

CREATE UNIQUE INDEX uk_pedido_idempotencia_usuario
    ON pedidos (tenant_id, usuario_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

CREATE INDEX idx_pedidos_direccion_entrega
    ON pedidos (tenant_id, direccion_entrega_id)
    WHERE direccion_entrega_id IS NOT NULL;
