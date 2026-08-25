-- Outbox transaccional para sincronización Web/Mobile. Los triggers escriben en
-- la misma transacción que el cambio; el worker entrega después del commit.
CREATE TABLE domain_event_outbox (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       BIGINT NOT NULL REFERENCES tenants(id),
    event_type      VARCHAR(80) NOT NULL,
    aggregate_type  VARCHAR(60) NOT NULL,
    aggregate_id    BIGINT NOT NULL,
    payload         JSONB NOT NULL DEFAULT '{}'::jsonb,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempts        INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    claimed_at      TIMESTAMP,
    processed_at    TIMESTAMP,
    occurred_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_error      VARCHAR(300),
    CONSTRAINT ck_domain_event_status CHECK (status IN ('PENDING', 'PROCESSING', 'PROCESSED'))
);

CREATE INDEX idx_domain_event_due ON domain_event_outbox(next_attempt_at, id)
    WHERE status = 'PENDING';
CREATE INDEX idx_domain_event_recovery ON domain_event_outbox(tenant_id, id);

ALTER TABLE domain_event_outbox ENABLE ROW LEVEL SECURITY;
ALTER TABLE domain_event_outbox FORCE ROW LEVEL SECURITY;
CREATE POLICY aislamiento_tenant ON domain_event_outbox
    USING (tenant_id = tenant_actual()) WITH CHECK (tenant_id = tenant_actual());
DO $$ BEGIN
    EXECUTE format($f$CREATE POLICY mantenimiento ON domain_event_outbox TO %I
        USING (tenant_actual() IS NULL) WITH CHECK (tenant_actual() IS NULL)$f$, current_user);
END $$;

CREATE OR REPLACE FUNCTION enqueue_domain_event() RETURNS trigger AS $$
DECLARE
    row_data JSONB;
    tenant BIGINT;
    aggregate BIGINT;
    action VARCHAR(20);
BEGIN
    row_data := CASE WHEN TG_OP = 'DELETE' THEN to_jsonb(OLD) ELSE to_jsonb(NEW) END;
    tenant := (row_data ->> 'tenant_id')::BIGINT;
    -- Algunas tablas centrales (por ejemplo configuracion) usan una clave
    -- compuesta y no poseen una columna id. El evento sigue siendo válido y
    -- provoca la reconsulta de la colección completa.
    aggregate := COALESCE((row_data ->> 'id')::BIGINT, 0);
    action := CASE TG_OP WHEN 'INSERT' THEN 'CREATED' WHEN 'UPDATE' THEN 'UPDATED' ELSE 'DELETED' END;

    INSERT INTO domain_event_outbox(tenant_id, event_type, aggregate_type, aggregate_id, payload)
    VALUES (tenant, TG_ARGV[0] || '_' || action, TG_ARGV[0], aggregate,
            jsonb_build_object('operation', TG_OP));

    IF TG_TABLE_NAME = 'productos' AND TG_OP = 'UPDATE'
       AND ((to_jsonb(OLD) ->> 'stock_real') IS DISTINCT FROM (to_jsonb(NEW) ->> 'stock_real')
            OR (to_jsonb(OLD) ->> 'stock_comprometido') IS DISTINCT FROM (to_jsonb(NEW) ->> 'stock_comprometido')) THEN
        INSERT INTO domain_event_outbox(tenant_id, event_type, aggregate_type, aggregate_id, payload)
        VALUES (tenant, 'STOCK_UPDATED', 'PRODUCT', aggregate,
                jsonb_build_object('stockReal', to_jsonb(NEW) -> 'stock_real',
                                   'stockComprometido', to_jsonb(NEW) -> 'stock_comprometido'));
    END IF;
    RETURN CASE WHEN TG_OP = 'DELETE' THEN OLD ELSE NEW END;
END;
$$ LANGUAGE plpgsql;

DO $$
DECLARE item RECORD;
BEGIN
    FOR item IN SELECT * FROM (VALUES
        ('usuarios', 'USER'), ('clientes', 'CLIENT'), ('proveedores', 'SUPPLIER'),
        ('marcas', 'BRAND'), ('modelos', 'MODEL'), ('productos', 'PRODUCT'),
        ('pedidos', 'ORDER'), ('pedido_detalles', 'ORDER_ITEM'), ('pagos', 'PAYMENT'),
        ('facturas', 'INVOICE'), ('notas_credito', 'CREDIT_NOTE'),
        ('movimiento_caja', 'CASH_MOVEMENT'), ('aperturas_caja', 'CASH_OPENING'),
        ('cierres_caja', 'CASH_CLOSING'), ('configuracion', 'CONFIGURATION'),
        ('direcciones_entrega', 'DELIVERY_ADDRESS'), ('envios', 'SHIPMENT'),
        ('tarifas_envio', 'SHIPPING_RATE'), ('proveedor_productos', 'SUPPLIER_OFFER'),
        ('compra_comprobantes', 'PURCHASE_DOCUMENT'), ('puntos_expedicion', 'ISSUING_POINT')
    ) AS events(table_name, aggregate_type)
    LOOP
        EXECUTE format('CREATE TRIGGER trg_sync_%I AFTER INSERT OR UPDATE OR DELETE ON %I '
            || 'FOR EACH ROW EXECUTE FUNCTION enqueue_domain_event(%L)',
            item.table_name, item.table_name, item.aggregate_type);
    END LOOP;
END $$;
