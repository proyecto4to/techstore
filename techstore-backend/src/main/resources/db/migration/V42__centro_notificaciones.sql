-- Centro de notificaciones persistente por usuario.

ALTER TABLE notification_outbox DROP CONSTRAINT ck_notification_outbox_channel;
ALTER TABLE notification_outbox ADD CONSTRAINT ck_notification_outbox_channel
    CHECK (consumer_channel IN ('EMAIL', 'PUSH', 'INTERNAL'));

CREATE TABLE notificaciones_usuario (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           BIGINT NOT NULL REFERENCES tenants(id),
    usuario_id          BIGINT NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    tipo                VARCHAR(40) NOT NULL,
    titulo              VARCHAR(160) NOT NULL,
    mensaje             VARCHAR(500) NOT NULL,
    referencia_tipo     VARCHAR(40) NOT NULL,
    referencia_id       BIGINT NOT NULL,
    deep_link           VARCHAR(255) NOT NULL,
    leida               BOOLEAN NOT NULL DEFAULT FALSE,
    leida_en            TIMESTAMP,
    source_outbox_id    BIGINT NOT NULL,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_notificacion_source UNIQUE (tenant_id, source_outbox_id)
);

CREATE INDEX idx_notificaciones_usuario_fecha
    ON notificaciones_usuario (tenant_id, usuario_id, created_at DESC);
CREATE INDEX idx_notificaciones_usuario_no_leidas
    ON notificaciones_usuario (tenant_id, usuario_id, created_at DESC)
    WHERE leida = FALSE;

ALTER TABLE notificaciones_usuario ENABLE ROW LEVEL SECURITY;
ALTER TABLE notificaciones_usuario FORCE ROW LEVEL SECURITY;

CREATE POLICY aislamiento_tenant ON notificaciones_usuario
    USING (tenant_id = tenant_actual()) WITH CHECK (tenant_id = tenant_actual());

DO $$
BEGIN
    EXECUTE format($f$CREATE POLICY mantenimiento ON notificaciones_usuario TO %I
        USING (tenant_actual() IS NULL) WITH CHECK (tenant_actual() IS NULL)$f$, current_user);
END $$;
