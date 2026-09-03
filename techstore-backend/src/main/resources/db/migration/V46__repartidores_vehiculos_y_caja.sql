-- Operación de reparto propia, caja del repartidor y flota de vehículos.
-- Se reutilizan usuarios, pedidos, envíos, caja, auditoría y notificaciones.

INSERT INTO roles (nombre, descripcion)
VALUES ('Repartidor', 'Operador de entregas y cobros a domicilio')
ON CONFLICT (nombre) DO NOTHING;

CREATE TABLE reparto_asignaciones (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           BIGINT NOT NULL REFERENCES tenants(id),
    pedido_id           BIGINT NOT NULL REFERENCES pedidos(id),
    envio_id            BIGINT NOT NULL REFERENCES envios(id),
    repartidor_id       BIGINT NOT NULL REFERENCES usuarios(id),
    asignado_por_id     BIGINT NOT NULL REFERENCES usuarios(id),
    estado              VARCHAR(30) NOT NULL,
    orden_entrega       INTEGER NOT NULL DEFAULT 1,
    observaciones       TEXT,
    fecha_asignacion    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_aceptacion    TIMESTAMP,
    fecha_inicio        TIMESTAMP,
    fecha_finalizacion  TIMESTAMP,
    resultado_final     TEXT,
    version             BIGINT NOT NULL DEFAULT 0,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ck_reparto_estado CHECK (estado IN (
        'ASIGNADO', 'ACEPTADO', 'EN_RUTA', 'EN_DESTINO',
        'ENTREGADO', 'NO_ENTREGADO', 'CANCELADO')),
    CONSTRAINT ck_reparto_orden CHECK (orden_entrega > 0)
);

CREATE UNIQUE INDEX uk_reparto_pedido_activo
    ON reparto_asignaciones (tenant_id, pedido_id)
    WHERE estado IN ('ASIGNADO', 'ACEPTADO', 'EN_RUTA', 'EN_DESTINO');
CREATE INDEX idx_reparto_repartidor_estado
    ON reparto_asignaciones (tenant_id, repartidor_id, estado, orden_entrega, fecha_asignacion);
CREATE INDEX idx_reparto_envio ON reparto_asignaciones (tenant_id, envio_id);

CREATE TABLE reparto_historial (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           BIGINT NOT NULL REFERENCES tenants(id),
    asignacion_id       BIGINT NOT NULL REFERENCES reparto_asignaciones(id),
    estado_anterior     VARCHAR(30),
    estado_nuevo        VARCHAR(30) NOT NULL,
    usuario_id          BIGINT NOT NULL REFERENCES usuarios(id),
    fecha               TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    observacion         TEXT
);

CREATE INDEX idx_reparto_historial_timeline
    ON reparto_historial (tenant_id, asignacion_id, fecha, id);

CREATE TABLE cobros_repartidor (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           BIGINT NOT NULL REFERENCES tenants(id),
    pedido_id           BIGINT NOT NULL REFERENCES pedidos(id),
    asignacion_id       BIGINT NOT NULL REFERENCES reparto_asignaciones(id),
    cliente_id          BIGINT NOT NULL REFERENCES usuarios(id),
    repartidor_id       BIGINT NOT NULL REFERENCES usuarios(id),
    monto               NUMERIC(15, 2) NOT NULL,
    moneda              moneda NOT NULL DEFAULT 'PYG',
    medio_pago          VARCHAR(30) NOT NULL DEFAULT 'Efectivo',
    fecha               TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    registrado_por_id   BIGINT NOT NULL REFERENCES usuarios(id),
    estado              VARCHAR(20) NOT NULL DEFAULT 'REGISTRADO',
    referencia          VARCHAR(100) NOT NULL,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ck_cobro_repartidor_monto CHECK (monto > 0),
    CONSTRAINT ck_cobro_repartidor_medio CHECK (medio_pago = 'Efectivo'),
    CONSTRAINT ck_cobro_repartidor_estado CHECK (estado IN ('REGISTRADO', 'REVERSADO')),
    CONSTRAINT uk_cobro_repartidor_pedido UNIQUE (tenant_id, pedido_id),
    CONSTRAINT uk_cobro_repartidor_asignacion UNIQUE (tenant_id, asignacion_id),
    CONSTRAINT uk_cobro_repartidor_referencia UNIQUE (tenant_id, referencia)
);

CREATE INDEX idx_cobros_repartidor_fecha
    ON cobros_repartidor (tenant_id, repartidor_id, fecha DESC);

CREATE TABLE movimientos_caja_repartidor (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           BIGINT NOT NULL REFERENCES tenants(id),
    repartidor_id       BIGINT NOT NULL REFERENCES usuarios(id),
    tipo_movimiento     VARCHAR(40) NOT NULL,
    naturaleza          VARCHAR(10) NOT NULL,
    monto               NUMERIC(15, 2) NOT NULL,
    moneda              moneda NOT NULL DEFAULT 'PYG',
    referencia_tipo     VARCHAR(40),
    referencia_id       BIGINT,
    fecha               TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    usuario_id          BIGINT NOT NULL REFERENCES usuarios(id),
    estado              VARCHAR(20) NOT NULL DEFAULT 'REGISTRADO',
    observacion         TEXT,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ck_mov_caja_rep_tipo CHECK (tipo_movimiento IN (
        'COBRO_PEDIDO', 'TRANSFERENCIA_A_CAJERO', 'AJUSTE_AUTORIZADO', 'REVERSO', 'OTRO')),
    CONSTRAINT ck_mov_caja_rep_naturaleza CHECK (naturaleza IN ('DEBITO', 'CREDITO')),
    CONSTRAINT ck_mov_caja_rep_monto CHECK (monto > 0),
    CONSTRAINT ck_mov_caja_rep_estado CHECK (estado IN ('REGISTRADO', 'REVERSADO'))
);

CREATE INDEX idx_mov_caja_rep_saldo
    ON movimientos_caja_repartidor (tenant_id, repartidor_id, estado, fecha, id);
CREATE UNIQUE INDEX uk_mov_caja_rep_referencia
    ON movimientos_caja_repartidor
        (tenant_id, repartidor_id, tipo_movimiento, naturaleza, referencia_tipo, referencia_id)
    WHERE referencia_tipo IS NOT NULL AND referencia_id IS NOT NULL AND estado = 'REGISTRADO';

CREATE TABLE cierres_caja_repartidor (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           BIGINT NOT NULL REFERENCES tenants(id),
    repartidor_id       BIGINT NOT NULL REFERENCES usuarios(id),
    cajero_id           BIGINT NOT NULL REFERENCES usuarios(id),
    saldo_cierre        NUMERIC(15, 2) NOT NULL,
    monto_solicitado    NUMERIC(15, 2) NOT NULL,
    moneda              moneda NOT NULL DEFAULT 'PYG',
    fecha               TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    estado              VARCHAR(30) NOT NULL,
    observacion         TEXT,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ck_cierre_rep_importes CHECK (
        saldo_cierre > 0 AND monto_solicitado > 0 AND monto_solicitado = saldo_cierre),
    CONSTRAINT ck_cierre_rep_estado CHECK (estado IN (
        'PENDIENTE_ACEPTACION', 'ACEPTADO', 'RECHAZADO', 'CANCELADO'))
);

CREATE UNIQUE INDEX uk_cierre_rep_pendiente
    ON cierres_caja_repartidor (tenant_id, repartidor_id)
    WHERE estado = 'PENDIENTE_ACEPTACION';

CREATE TABLE transferencias_repartidor_cajero (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           BIGINT NOT NULL REFERENCES tenants(id),
    repartidor_id       BIGINT NOT NULL REFERENCES usuarios(id),
    cajero_id           BIGINT NOT NULL REFERENCES usuarios(id),
    cierre_id           BIGINT NOT NULL REFERENCES cierres_caja_repartidor(id),
    monto               NUMERIC(15, 2) NOT NULL,
    moneda              moneda NOT NULL DEFAULT 'PYG',
    estado              VARCHAR(30) NOT NULL,
    fecha_solicitud     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_respuesta     TIMESTAMP,
    respondido_por_id   BIGINT REFERENCES usuarios(id),
    motivo_rechazo      TEXT,
    observacion         TEXT,
    version             BIGINT NOT NULL DEFAULT 0,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ck_transferencia_rep_monto CHECK (monto > 0),
    CONSTRAINT ck_transferencia_rep_estado CHECK (estado IN (
        'PENDIENTE_ACEPTACION', 'ACEPTADO', 'RECHAZADO', 'CANCELADO')),
    CONSTRAINT ck_transferencia_rep_rechazo CHECK (
        estado <> 'RECHAZADO' OR (motivo_rechazo IS NOT NULL AND length(trim(motivo_rechazo)) > 0)),
    CONSTRAINT uk_transferencia_rep_cierre UNIQUE (tenant_id, cierre_id)
);

CREATE INDEX idx_transferencias_cajero_estado
    ON transferencias_repartidor_cajero (tenant_id, cajero_id, estado, fecha_solicitud DESC);
CREATE INDEX idx_transferencias_repartidor_fecha
    ON transferencias_repartidor_cajero (tenant_id, repartidor_id, fecha_solicitud DESC);

CREATE TABLE vehiculos (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           BIGINT NOT NULL REFERENCES tenants(id),
    matricula           VARCHAR(20) NOT NULL,
    marca               VARCHAR(80) NOT NULL,
    modelo              VARCHAR(80) NOT NULL,
    anio                INTEGER,
    tipo                VARCHAR(40) NOT NULL,
    color               VARCHAR(40),
    numero_chasis       VARCHAR(100),
    kilometraje_actual  NUMERIC(12, 1) NOT NULL DEFAULT 0,
    tipo_combustible    VARCHAR(40),
    estado              VARCHAR(30) NOT NULL DEFAULT 'DISPONIBLE',
    activo              BOOLEAN NOT NULL DEFAULT TRUE,
    observaciones       TEXT,
    fecha_alta          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version             BIGINT NOT NULL DEFAULT 0,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_vehiculo_matricula UNIQUE (tenant_id, matricula),
    CONSTRAINT ck_vehiculo_anio CHECK (anio IS NULL OR anio BETWEEN 1900 AND 2200),
    CONSTRAINT ck_vehiculo_km CHECK (kilometraje_actual >= 0),
    CONSTRAINT ck_vehiculo_estado CHECK (estado IN (
        'DISPONIBLE', 'EN_USO', 'MANTENIMIENTO', 'FUERA_DE_SERVICIO', 'INACTIVO'))
);

CREATE INDEX idx_vehiculos_estado ON vehiculos (tenant_id, activo, estado, matricula);

CREATE TABLE usos_vehiculo (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           BIGINT NOT NULL REFERENCES tenants(id),
    vehiculo_id         BIGINT NOT NULL REFERENCES vehiculos(id),
    repartidor_id       BIGINT NOT NULL REFERENCES usuarios(id),
    km_inicio           NUMERIC(12, 1) NOT NULL,
    combustible_inicio  NUMERIC(5, 2) NOT NULL,
    fecha_inicio        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    km_fin              NUMERIC(12, 1),
    combustible_fin     NUMERIC(5, 2),
    fecha_fin           TIMESTAMP,
    estado              VARCHAR(20) NOT NULL DEFAULT 'ACTIVO',
    observacion_inicio  TEXT,
    observacion_fin     TEXT,
    version             BIGINT NOT NULL DEFAULT 0,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ck_uso_vehiculo_km CHECK (km_inicio >= 0 AND (km_fin IS NULL OR km_fin >= km_inicio)),
    CONSTRAINT ck_uso_vehiculo_combustible CHECK (
        combustible_inicio BETWEEN 0 AND 100
        AND (combustible_fin IS NULL OR combustible_fin BETWEEN 0 AND 100)),
    CONSTRAINT ck_uso_vehiculo_estado CHECK (estado IN ('ACTIVO', 'FINALIZADO', 'CANCELADO'))
);

CREATE UNIQUE INDEX uk_uso_vehiculo_activo
    ON usos_vehiculo (tenant_id, vehiculo_id) WHERE estado = 'ACTIVO';
CREATE UNIQUE INDEX uk_uso_repartidor_activo
    ON usos_vehiculo (tenant_id, repartidor_id) WHERE estado = 'ACTIVO';
CREATE INDEX idx_usos_vehiculo_historial
    ON usos_vehiculo (tenant_id, vehiculo_id, fecha_inicio DESC);

CREATE TABLE eventos_vehiculo (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           BIGINT NOT NULL REFERENCES tenants(id),
    uso_vehiculo_id     BIGINT NOT NULL REFERENCES usos_vehiculo(id),
    vehiculo_id         BIGINT NOT NULL REFERENCES vehiculos(id),
    repartidor_id       BIGINT NOT NULL REFERENCES usuarios(id),
    tipo_evento         VARCHAR(40) NOT NULL,
    fecha               TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    kilometraje         NUMERIC(12, 1) NOT NULL,
    descripcion         TEXT NOT NULL,
    ubicacion           VARCHAR(255),
    importe             NUMERIC(15, 2),
    datos_adicionales   JSONB NOT NULL DEFAULT '{}'::jsonb,
    observacion         TEXT,
    requiere_atencion   BOOLEAN NOT NULL DEFAULT FALSE,
    resuelto            BOOLEAN NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ck_evento_vehiculo_tipo CHECK (tipo_evento IN (
        'RECARGA_COMBUSTIBLE', 'REPARACION', 'AVERIA', 'ESTANCAMIENTO',
        'ACCIDENTE', 'MANTENIMIENTO', 'OBSERVACION', 'OTRO')),
    CONSTRAINT ck_evento_vehiculo_km CHECK (kilometraje >= 0),
    CONSTRAINT ck_evento_vehiculo_importe CHECK (importe IS NULL OR importe >= 0)
);

CREATE INDEX idx_eventos_vehiculo_timeline
    ON eventos_vehiculo (tenant_id, vehiculo_id, fecha DESC, id DESC);
CREATE INDEX idx_eventos_vehiculo_pendientes
    ON eventos_vehiculo (tenant_id, vehiculo_id, requiere_atencion, resuelto)
    WHERE requiere_atencion = TRUE AND resuelto = FALSE;

DO $$
DECLARE
    t TEXT;
    tablas TEXT[] := ARRAY[
        'reparto_asignaciones', 'reparto_historial', 'cobros_repartidor',
        'movimientos_caja_repartidor', 'cierres_caja_repartidor',
        'transferencias_repartidor_cajero', 'vehiculos', 'usos_vehiculo',
        'eventos_vehiculo'
    ];
BEGIN
    FOREACH t IN ARRAY tablas LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', t);
        EXECUTE format($f$CREATE POLICY aislamiento_tenant ON %I
            USING (tenant_id = tenant_actual()) WITH CHECK (tenant_id = tenant_actual())$f$, t);
        EXECUTE format($f$CREATE POLICY mantenimiento ON %I TO %I
            USING (tenant_actual() IS NULL) WITH CHECK (tenant_actual() IS NULL)$f$, t, current_user);
    END LOOP;
END $$;
