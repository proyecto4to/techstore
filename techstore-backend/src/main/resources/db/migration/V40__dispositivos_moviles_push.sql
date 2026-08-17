-- Instalaciones móviles autorizadas para recibir notificaciones push.

CREATE TABLE dispositivos_moviles (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           BIGINT NOT NULL REFERENCES tenants(id),
    usuario_id          BIGINT NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    plataforma          VARCHAR(20) NOT NULL,
    push_token          VARCHAR(512) NOT NULL,
    device_identifier   VARCHAR(120) NOT NULL,
    activo              BOOLEAN NOT NULL DEFAULT TRUE,
    ultimo_uso          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_dispositivo_instalacion UNIQUE (tenant_id, device_identifier),
    CONSTRAINT ck_dispositivo_plataforma CHECK (plataforma IN ('ANDROID', 'IOS')),
    CONSTRAINT ck_dispositivo_push_token CHECK (char_length(push_token) BETWEEN 20 AND 512),
    CONSTRAINT ck_dispositivo_identifier CHECK (char_length(device_identifier) BETWEEN 16 AND 120)
);

CREATE INDEX idx_dispositivos_usuario_activos
    ON dispositivos_moviles (tenant_id, usuario_id, ultimo_uso DESC)
    WHERE activo = TRUE;
CREATE INDEX idx_dispositivos_push_token_activos
    ON dispositivos_moviles (tenant_id, push_token)
    WHERE activo = TRUE;

ALTER TABLE dispositivos_moviles ENABLE ROW LEVEL SECURITY;
ALTER TABLE dispositivos_moviles FORCE ROW LEVEL SECURITY;

CREATE POLICY aislamiento_tenant ON dispositivos_moviles
    USING (tenant_id = tenant_actual()) WITH CHECK (tenant_id = tenant_actual());

DO $$
BEGIN
    EXECUTE format($f$CREATE POLICY mantenimiento ON dispositivos_moviles TO %I
        USING (tenant_actual() IS NULL) WITH CHECK (tenant_actual() IS NULL)$f$, current_user);
END $$;
