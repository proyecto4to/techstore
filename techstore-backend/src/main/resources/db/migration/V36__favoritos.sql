-- Favoritos del catálogo por usuario y tenant.
-- El producto se vuelve a validar al leer: una baja lógica no debe seguir
-- apareciendo solo porque alguien lo marcó antes.

CREATE TABLE favoritos (
    id          BIGSERIAL PRIMARY KEY,
    tenant_id   BIGINT    NOT NULL REFERENCES tenants(id),
    usuario_id  BIGINT    NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    producto_id BIGINT    NOT NULL REFERENCES productos(id) ON DELETE CASCADE,
    creado_en   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT favorito_usuario_producto_unico
        UNIQUE (tenant_id, usuario_id, producto_id)
);

CREATE INDEX idx_favoritos_usuario_fecha
    ON favoritos (tenant_id, usuario_id, creado_en DESC);

CREATE INDEX idx_favoritos_producto
    ON favoritos (tenant_id, producto_id);

ALTER TABLE favoritos ENABLE ROW LEVEL SECURITY;
ALTER TABLE favoritos FORCE ROW LEVEL SECURITY;

CREATE POLICY aislamiento_tenant ON favoritos
    USING (tenant_id = tenant_actual())
    WITH CHECK (tenant_id = tenant_actual());

DO $$
BEGIN
    EXECUTE format($f$
        CREATE POLICY mantenimiento ON favoritos
            TO %I
            USING (tenant_actual() IS NULL)
            WITH CHECK (tenant_actual() IS NULL)
    $f$, current_user);
END $$;
