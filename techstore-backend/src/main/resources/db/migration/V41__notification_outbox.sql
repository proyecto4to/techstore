-- Eventos de negocio pendientes de entregar por canales externos.

CREATE TABLE notification_outbox (
    id                    BIGSERIAL PRIMARY KEY,
    tenant_id             BIGINT NOT NULL REFERENCES tenants(id),
    event_type            VARCHAR(40) NOT NULL,
    consumer_channel      VARCHAR(20) NOT NULL,
    aggregate_type        VARCHAR(40) NOT NULL,
    aggregate_id          BIGINT NOT NULL,
    recipient_user_id     BIGINT REFERENCES usuarios(id) ON DELETE SET NULL,
    payload               JSONB NOT NULL DEFAULT '{}'::jsonb,
    status                VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempts              INTEGER NOT NULL DEFAULT 0,
    next_attempt_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    claimed_at            TIMESTAMP,
    processed_at          TIMESTAMP,
    created_at            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_error_sanitized  VARCHAR(500),
    idempotency_key       VARCHAR(220) NOT NULL,

    CONSTRAINT uk_notification_outbox_idempotency UNIQUE (tenant_id, idempotency_key),
    CONSTRAINT ck_notification_outbox_status
        CHECK (status IN ('PENDING', 'PROCESSING', 'PROCESSED', 'EXHAUSTED')),
    CONSTRAINT ck_notification_outbox_channel
        CHECK (consumer_channel IN ('EMAIL', 'PUSH')),
    CONSTRAINT ck_notification_outbox_attempts CHECK (attempts >= 0)
);

CREATE INDEX idx_notification_outbox_due
    ON notification_outbox (next_attempt_at, created_at)
    WHERE status = 'PENDING';
CREATE INDEX idx_notification_outbox_stale
    ON notification_outbox (claimed_at)
    WHERE status = 'PROCESSING';
CREATE INDEX idx_notification_outbox_aggregate
    ON notification_outbox (tenant_id, aggregate_type, aggregate_id, event_type);

ALTER TABLE notification_outbox ENABLE ROW LEVEL SECURITY;
ALTER TABLE notification_outbox FORCE ROW LEVEL SECURITY;

CREATE POLICY aislamiento_tenant ON notification_outbox
    USING (tenant_id = tenant_actual()) WITH CHECK (tenant_id = tenant_actual());

DO $$
BEGIN
    EXECUTE format($f$CREATE POLICY mantenimiento ON notification_outbox TO %I
        USING (tenant_actual() IS NULL) WITH CHECK (tenant_actual() IS NULL)$f$, current_user);
END $$;
