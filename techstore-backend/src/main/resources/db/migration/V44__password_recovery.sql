CREATE UNIQUE INDEX uq_usuarios_id_tenant ON usuarios(id, tenant_id);

CREATE TABLE password_reset_tokens (
    id          BIGSERIAL PRIMARY KEY,
    tenant_id   BIGINT NOT NULL REFERENCES tenants(id),
    usuario_id  BIGINT NOT NULL,
    token_hash  VARCHAR(64) NOT NULL UNIQUE,
    creado_en   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expira_en   TIMESTAMP NOT NULL,
    usado_en    TIMESTAMP,
    CONSTRAINT ck_password_reset_expiry CHECK (expira_en > creado_en),
    CONSTRAINT fk_password_reset_user_tenant
        FOREIGN KEY (usuario_id, tenant_id) REFERENCES usuarios(id, tenant_id) ON DELETE CASCADE
);

CREATE INDEX idx_password_reset_user_active
    ON password_reset_tokens(tenant_id, usuario_id, expira_en)
    WHERE usado_en IS NULL;

ALTER TABLE password_reset_tokens ENABLE ROW LEVEL SECURITY;
ALTER TABLE password_reset_tokens FORCE ROW LEVEL SECURITY;
CREATE POLICY aislamiento_tenant ON password_reset_tokens
    USING (tenant_id = tenant_actual()) WITH CHECK (tenant_id = tenant_actual());
DO $$ BEGIN
    EXECUTE format($f$CREATE POLICY mantenimiento ON password_reset_tokens TO %I
        USING (tenant_actual() IS NULL) WITH CHECK (tenant_actual() IS NULL)$f$, current_user);
END $$;
