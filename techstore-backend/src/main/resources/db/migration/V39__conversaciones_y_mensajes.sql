-- Conversaciones cliente <-> TechStore con recuperación REST y entrega STOMP.

CREATE TABLE conversaciones (
    id                 BIGSERIAL PRIMARY KEY,
    tenant_id          BIGINT NOT NULL REFERENCES tenants(id),
    cliente_id         BIGINT NOT NULL REFERENCES usuarios(id),
    pedido_id          BIGINT REFERENCES pedidos(id) ON DELETE SET NULL,
    estado             VARCHAR(20) NOT NULL DEFAULT 'ABIERTA',
    ultimo_mensaje_en  TIMESTAMP,
    created_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_conversacion_estado CHECK (estado IN ('ABIERTA', 'CERRADA'))
);

CREATE UNIQUE INDEX uk_conversacion_abierta_contexto
    ON conversaciones (tenant_id, cliente_id, COALESCE(pedido_id, 0))
    WHERE estado = 'ABIERTA';

CREATE INDEX idx_conversaciones_cliente
    ON conversaciones (tenant_id, cliente_id, COALESCE(ultimo_mensaje_en, created_at) DESC);
CREATE INDEX idx_conversaciones_inbox
    ON conversaciones (tenant_id, estado, COALESCE(ultimo_mensaje_en, created_at) DESC);

CREATE TABLE mensajes (
    id                 BIGSERIAL PRIMARY KEY,
    tenant_id          BIGINT NOT NULL REFERENCES tenants(id),
    conversacion_id    BIGINT NOT NULL REFERENCES conversaciones(id) ON DELETE CASCADE,
    remitente_id       BIGINT NOT NULL REFERENCES usuarios(id),
    client_message_id  VARCHAR(100) NOT NULL,
    tipo               VARCHAR(20) NOT NULL,
    contenido          TEXT NOT NULL,
    enviado_en         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    entregado_en       TIMESTAMP,
    leido_en           TIMESTAMP,
    created_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_mensaje_cliente UNIQUE (tenant_id, remitente_id, client_message_id),
    CONSTRAINT ck_mensaje_tipo CHECK (tipo IN ('TEXTO', 'IMAGEN', 'SISTEMA')),
    CONSTRAINT ck_mensaje_contenido CHECK (char_length(contenido) BETWEEN 1 AND 2000)
);

CREATE INDEX idx_mensajes_historial
    ON mensajes (tenant_id, conversacion_id, enviado_en DESC, id DESC);
CREATE INDEX idx_mensajes_rate_limit
    ON mensajes (tenant_id, remitente_id, enviado_en DESC);
CREATE INDEX idx_mensajes_no_leidos
    ON mensajes (tenant_id, conversacion_id, remitente_id)
    WHERE leido_en IS NULL;

ALTER TABLE conversaciones ENABLE ROW LEVEL SECURITY;
ALTER TABLE conversaciones FORCE ROW LEVEL SECURITY;
ALTER TABLE mensajes ENABLE ROW LEVEL SECURITY;
ALTER TABLE mensajes FORCE ROW LEVEL SECURITY;

CREATE POLICY aislamiento_tenant ON conversaciones
    USING (tenant_id = tenant_actual()) WITH CHECK (tenant_id = tenant_actual());
CREATE POLICY aislamiento_tenant ON mensajes
    USING (tenant_id = tenant_actual()) WITH CHECK (tenant_id = tenant_actual());

DO $$
BEGIN
    EXECUTE format($f$CREATE POLICY mantenimiento ON conversaciones TO %I
        USING (tenant_actual() IS NULL) WITH CHECK (tenant_actual() IS NULL)$f$, current_user);
    EXECUTE format($f$CREATE POLICY mantenimiento ON mensajes TO %I
        USING (tenant_actual() IS NULL) WITH CHECK (tenant_actual() IS NULL)$f$, current_user);
END $$;
