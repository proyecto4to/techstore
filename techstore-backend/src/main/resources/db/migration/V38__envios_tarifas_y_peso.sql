-- Tarifas configurables, envíos y trazabilidad para ventas omnicanal.

ALTER TABLE productos
    ADD COLUMN peso_kg NUMERIC(10, 3) NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_producto_peso_no_negativo CHECK (peso_kg >= 0);

ALTER TABLE pedidos
    ADD COLUMN costo_envio NUMERIC(15, 2) NOT NULL DEFAULT 0,
    ADD COLUMN iva_envio NUMERIC(3) NOT NULL DEFAULT 10,
    ADD CONSTRAINT ck_pedido_costo_envio_no_negativo CHECK (costo_envio >= 0),
    ADD CONSTRAINT ck_pedido_iva_envio CHECK (iva_envio IN (0, 5, 10));

CREATE TABLE tarifas_envio (
    id                    BIGSERIAL PRIMARY KEY,
    tenant_id             BIGINT NOT NULL REFERENCES tenants(id),
    codigo                VARCHAR(80) NOT NULL,
    nombre                VARCHAR(120) NOT NULL,
    descripcion           VARCHAR(300),
    tipo                  VARCHAR(40) NOT NULL,
    provider_code         VARCHAR(80) NOT NULL DEFAULT 'DATABASE',
    transportista         VARCHAR(120),
    departamento          VARCHAR(80),
    ciudad                VARCHAR(100),
    zona                  VARCHAR(100),
    peso_min_kg           NUMERIC(10, 3),
    peso_max_kg           NUMERIC(10, 3),
    subtotal_min          NUMERIC(15, 2),
    subtotal_max          NUMERIC(15, 2),
    costo                 NUMERIC(15, 2) NOT NULL,
    umbral_envio_gratis   NUMERIC(15, 2),
    iva                   NUMERIC(3) NOT NULL DEFAULT 10,
    dias_estimados_min    INTEGER NOT NULL DEFAULT 0,
    dias_estimados_max    INTEGER NOT NULL DEFAULT 0,
    prioridad             INTEGER NOT NULL DEFAULT 100,
    promocion             BOOLEAN NOT NULL DEFAULT FALSE,
    vigencia_desde        TIMESTAMP,
    vigencia_hasta        TIMESTAMP,
    activa                BOOLEAN NOT NULL DEFAULT TRUE,
    created_at            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_tarifa_envio_codigo UNIQUE (tenant_id, codigo),
    CONSTRAINT ck_tarifa_envio_tipo CHECK (tipo IN (
        'RETIRO_TIENDA', 'ENTREGA_LOCAL', 'OTRA_CIUDAD',
        'OTRO_DEPARTAMENTO', 'TRANSPORTADORA')),
    CONSTRAINT ck_tarifa_envio_importes CHECK (
        costo >= 0
        AND (umbral_envio_gratis IS NULL OR umbral_envio_gratis >= 0)
        AND (subtotal_min IS NULL OR subtotal_min >= 0)
        AND (subtotal_max IS NULL OR subtotal_max >= subtotal_min)),
    CONSTRAINT ck_tarifa_envio_pesos CHECK (
        (peso_min_kg IS NULL OR peso_min_kg >= 0)
        AND (peso_max_kg IS NULL OR peso_max_kg >= peso_min_kg)),
    CONSTRAINT ck_tarifa_envio_iva CHECK (iva IN (0, 5, 10)),
    CONSTRAINT ck_tarifa_envio_dias CHECK (
        dias_estimados_min >= 0 AND dias_estimados_max >= dias_estimados_min),
    CONSTRAINT ck_tarifa_envio_vigencia CHECK (
        vigencia_hasta IS NULL OR vigencia_desde IS NULL OR vigencia_hasta >= vigencia_desde)
);

CREATE INDEX idx_tarifas_envio_activas
    ON tarifas_envio (tenant_id, activa, prioridad, id);

CREATE TABLE envios (
    id                    BIGSERIAL PRIMARY KEY,
    tenant_id             BIGINT NOT NULL REFERENCES tenants(id),
    pedido_id             BIGINT NOT NULL REFERENCES pedidos(id),
    direccion_entrega_id  BIGINT REFERENCES direcciones_entrega(id) ON DELETE SET NULL,
    tarifa_envio_id       BIGINT REFERENCES tarifas_envio(id) ON DELETE SET NULL,
    tipo                  VARCHAR(40) NOT NULL,
    provider_code         VARCHAR(80) NOT NULL,
    transportista         VARCHAR(120),
    codigo_seguimiento    VARCHAR(120),
    moneda                moneda NOT NULL DEFAULT 'PYG',
    costo                 NUMERIC(15, 2) NOT NULL DEFAULT 0,
    iva                   NUMERIC(3) NOT NULL DEFAULT 10,
    fecha_estimada_desde  DATE,
    fecha_estimada_hasta  DATE,
    fecha_despacho        TIMESTAMP,
    fecha_entrega         TIMESTAMP,
    estado                VARCHAR(40) NOT NULL,
    observaciones         TEXT,
    created_at            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_envio_pedido UNIQUE (tenant_id, pedido_id),
    CONSTRAINT ck_envio_tipo CHECK (tipo IN (
        'RETIRO_TIENDA', 'ENTREGA_LOCAL', 'OTRA_CIUDAD',
        'OTRO_DEPARTAMENTO', 'TRANSPORTADORA')),
    CONSTRAINT ck_envio_estado CHECK (estado IN (
        'PENDIENTE', 'PREPARANDO', 'LISTO_PARA_ENVIO', 'DESPACHADO',
        'EN_CAMINO', 'INTENTO_ENTREGA', 'ENTREGADO', 'CANCELADO')),
    CONSTRAINT ck_envio_costo CHECK (costo >= 0),
    CONSTRAINT ck_envio_iva CHECK (iva IN (0, 5, 10))
);

CREATE INDEX idx_envios_tracking ON envios (tenant_id, codigo_seguimiento)
    WHERE codigo_seguimiento IS NOT NULL;

CREATE TABLE envio_eventos (
    id           BIGSERIAL PRIMARY KEY,
    tenant_id    BIGINT NOT NULL REFERENCES tenants(id),
    envio_id     BIGINT NOT NULL REFERENCES envios(id) ON DELETE CASCADE,
    estado       VARCHAR(40) NOT NULL,
    descripcion  VARCHAR(300) NOT NULL,
    fecha_hora   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ubicacion    VARCHAR(200),

    CONSTRAINT ck_envio_evento_estado CHECK (estado IN (
        'PENDIENTE', 'PREPARANDO', 'LISTO_PARA_ENVIO', 'DESPACHADO',
        'EN_CAMINO', 'INTENTO_ENTREGA', 'ENTREGADO', 'CANCELADO'))
);

CREATE INDEX idx_envio_eventos_timeline
    ON envio_eventos (tenant_id, envio_id, fecha_hora, id);

ALTER TABLE tarifas_envio ENABLE ROW LEVEL SECURITY;
ALTER TABLE tarifas_envio FORCE ROW LEVEL SECURITY;
ALTER TABLE envios ENABLE ROW LEVEL SECURITY;
ALTER TABLE envios FORCE ROW LEVEL SECURITY;
ALTER TABLE envio_eventos ENABLE ROW LEVEL SECURITY;
ALTER TABLE envio_eventos FORCE ROW LEVEL SECURITY;

CREATE POLICY aislamiento_tenant ON tarifas_envio
    USING (tenant_id = tenant_actual()) WITH CHECK (tenant_id = tenant_actual());
CREATE POLICY aislamiento_tenant ON envios
    USING (tenant_id = tenant_actual()) WITH CHECK (tenant_id = tenant_actual());
CREATE POLICY aislamiento_tenant ON envio_eventos
    USING (tenant_id = tenant_actual()) WITH CHECK (tenant_id = tenant_actual());

DO $$
BEGIN
    EXECUTE format($f$CREATE POLICY mantenimiento ON tarifas_envio TO %I
        USING (tenant_actual() IS NULL) WITH CHECK (tenant_actual() IS NULL)$f$, current_user);
    EXECUTE format($f$CREATE POLICY mantenimiento ON envios TO %I
        USING (tenant_actual() IS NULL) WITH CHECK (tenant_actual() IS NULL)$f$, current_user);
    EXECUTE format($f$CREATE POLICY mantenimiento ON envio_eventos TO %I
        USING (tenant_actual() IS NULL) WITH CHECK (tenant_actual() IS NULL)$f$, current_user);
END $$;

-- Valores iniciales editables por tenant. Son datos de arranque del backend,
-- nunca tarifas incrustadas en la aplicación móvil.
INSERT INTO tarifas_envio (
    tenant_id, codigo, nombre, descripcion, tipo, transportista,
    departamento, ciudad, peso_min_kg, peso_max_kg, costo,
    umbral_envio_gratis, iva, dias_estimados_min, dias_estimados_max, prioridad)
SELECT id, 'RETIRO_TIENDA', 'Retiro en tienda', 'Retiro gratuito cuando el pedido esté preparado',
       'RETIRO_TIENDA', NULL, NULL, NULL, NULL, NULL, 0, NULL, 0, 0, 0, 1
FROM tenants;

INSERT INTO tarifas_envio (
    tenant_id, codigo, nombre, descripcion, tipo, transportista,
    departamento, ciudad, peso_min_kg, peso_max_kg, costo,
    umbral_envio_gratis, iva, dias_estimados_min, dias_estimados_max, prioridad)
SELECT id, 'ASUNCION_LOCAL', 'Entrega local en Asunción', 'Entrega urbana coordinada por TechStore',
       'ENTREGA_LOCAL', 'TechStore', NULL, 'Asunción', NULL, NULL, 25000, 1000000, 10, 1, 2, 10
FROM tenants;

INSERT INTO tarifas_envio (
    tenant_id, codigo, nombre, descripcion, tipo, transportista,
    departamento, ciudad, peso_min_kg, peso_max_kg, costo,
    umbral_envio_gratis, iva, dias_estimados_min, dias_estimados_max, prioridad)
SELECT id, 'CENTRAL_OTRA_CIUDAD', 'Entrega en Central', 'Entrega a otras ciudades del departamento Central',
       'OTRA_CIUDAD', 'TechStore', 'Central', NULL, NULL, NULL, 35000, 1200000, 10, 2, 4, 20
FROM tenants;

INSERT INTO tarifas_envio (
    tenant_id, codigo, nombre, descripcion, tipo, transportista,
    departamento, ciudad, peso_min_kg, peso_max_kg, costo,
    umbral_envio_gratis, iva, dias_estimados_min, dias_estimados_max, prioridad)
SELECT id, 'NACIONAL', 'Entrega nacional', 'Entrega en otras ciudades y departamentos',
       'OTRO_DEPARTAMENTO', 'TechStore', NULL, NULL, NULL, NULL, 55000, 1500000, 10, 3, 7, 50
FROM tenants;

INSERT INTO tarifas_envio (
    tenant_id, codigo, nombre, descripcion, tipo, transportista,
    departamento, ciudad, peso_min_kg, peso_max_kg, costo,
    umbral_envio_gratis, iva, dias_estimados_min, dias_estimados_max, prioridad)
SELECT id, 'TRANSPORTADORA_0_5KG', 'Transportadora nacional', 'Encomienda de hasta 5 kg',
       'TRANSPORTADORA', 'Transportadora a coordinar', NULL, NULL, 0, 5, 50000, NULL, 10, 3, 8, 60
FROM tenants;

INSERT INTO tarifas_envio (
    tenant_id, codigo, nombre, descripcion, tipo, transportista,
    departamento, ciudad, peso_min_kg, peso_max_kg, costo,
    umbral_envio_gratis, iva, dias_estimados_min, dias_estimados_max, prioridad)
SELECT id, 'TRANSPORTADORA_5_20KG', 'Transportadora nacional (carga)', 'Encomienda de más de 5 kg y hasta 20 kg',
       'TRANSPORTADORA', 'Transportadora a coordinar', NULL, NULL, 5.001, 20, 75000, NULL, 10, 4, 10, 61
FROM tenants;
