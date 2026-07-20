-- Cada movimiento de caja registra su medio (efectivo o transferencia),
-- para que el cierre de caja concilie el efectivo físico por separado.
ALTER TABLE movimiento_caja ADD COLUMN medio tipo_pago;

-- Los movimientos históricos: transferencia si el concepto lo indica, efectivo el resto.
UPDATE movimiento_caja SET medio = 'Transferencia' WHERE concepto LIKE '%(Transferencia)%';
UPDATE movimiento_caja SET medio = 'Efectivo' WHERE medio IS NULL;
ALTER TABLE movimiento_caja ALTER COLUMN medio SET NOT NULL;
ALTER TABLE movimiento_caja ALTER COLUMN medio SET DEFAULT 'Efectivo';

INSERT INTO autonumerador (entidad, valor) VALUES ('CIERRECAJA', 0);

-- Arqueo de un turno: desde el cierre anterior hasta el momento del cierre.
CREATE TABLE cierres_caja (
    id BIGSERIAL PRIMARY KEY,
    numero INTEGER NOT NULL UNIQUE,
    fecha_desde TIMESTAMP NOT NULL,
    fecha_cierre TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    usuario_id BIGINT NOT NULL,
    ingresos_efectivo NUMERIC(15, 2) NOT NULL,
    egresos_efectivo NUMERIC(15, 2) NOT NULL,
    ingresos_transferencia NUMERIC(15, 2) NOT NULL,
    efectivo_teorico NUMERIC(15, 2) NOT NULL,
    efectivo_contado NUMERIC(15, 2) NOT NULL,
    diferencia NUMERIC(15, 2) NOT NULL,
    notas TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_cierre_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id)
);

CREATE INDEX idx_cierres_fecha ON cierres_caja(fecha_cierre);
