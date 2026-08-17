-- Sesiones refresh revocables y rotativas.
--
-- El JWT de refresh nunca se guarda en claro: solo su SHA-256 y el jti firmado.
-- Esto permite cerrar sesión, detectar reutilización y rotar el token sin
-- convertir una copia de la base en una colección de credenciales utilizables.

CREATE TABLE sesiones_refresh (
    id               BIGSERIAL PRIMARY KEY,
    tenant_id        BIGINT       NOT NULL DEFAULT 1 REFERENCES tenants(id),
    usuario_id       BIGINT       NOT NULL REFERENCES usuarios(id),
    token_id         UUID         NOT NULL,
    token_hash       VARCHAR(64)  NOT NULL,
    creado_en        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expira_en        TIMESTAMP    NOT NULL,
    revocado_en      TIMESTAMP,
    reemplazado_por  UUID,

    CONSTRAINT sesion_refresh_token_unico UNIQUE (tenant_id, token_id),
    CONSTRAINT sesion_refresh_hash_unico UNIQUE (tenant_id, token_hash),
    CONSTRAINT sesion_refresh_fechas_validas CHECK (expira_en > creado_en)
);

CREATE INDEX idx_sesiones_refresh_usuario_activas
    ON sesiones_refresh (tenant_id, usuario_id, expira_en)
    WHERE revocado_en IS NULL;

CREATE INDEX idx_sesiones_refresh_expiracion
    ON sesiones_refresh (expira_en);

COMMENT ON TABLE sesiones_refresh IS
    'Sesiones refresh rotativas. Guarda SHA-256 del token, nunca el JWT utilizable.';
COMMENT ON COLUMN sesiones_refresh.reemplazado_por IS
    'jti del token que sustituyó a esta sesión durante una rotación correcta.';

ALTER TABLE sesiones_refresh ENABLE ROW LEVEL SECURITY;
ALTER TABLE sesiones_refresh FORCE ROW LEVEL SECURITY;

CREATE POLICY aislamiento_tenant ON sesiones_refresh
    USING (tenant_id = tenant_actual())
    WITH CHECK (tenant_id = tenant_actual());

DO $$
BEGIN
    EXECUTE format($f$
        CREATE POLICY mantenimiento ON sesiones_refresh
            TO %I
            USING (tenant_actual() IS NULL)
            WITH CHECK (tenant_actual() IS NULL)
    $f$, current_user);
END $$;
