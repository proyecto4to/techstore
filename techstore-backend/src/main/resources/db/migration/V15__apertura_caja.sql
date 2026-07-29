-- Apertura de caja: faltaba la contraparte del cierre. Sin ella, el efectivo
-- teorico era solo (ingresos - egresos) del turno e ignoraba la plata que
-- quedaba fisicamente en el cajon, asi que el arqueo daba diferencia todos los
-- dias salvo que se vaciara la caja por completo en cada cierre.
CREATE TABLE aperturas_caja (
    id BIGSERIAL PRIMARY KEY,
    numero INTEGER NOT NULL UNIQUE,
    fecha_apertura TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    usuario_id BIGINT NOT NULL,
    saldo_inicial NUMERIC(15, 2) NOT NULL,
    notas TEXT,
    cierre_id BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_apertura_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id),
    CONSTRAINT fk_apertura_cierre FOREIGN KEY (cierre_id) REFERENCES cierres_caja(id)
);

CREATE INDEX idx_aperturas_fecha ON aperturas_caja(fecha_apertura);

-- Solo puede haber una caja abierta a la vez (cierre_id nulo = turno en curso).
CREATE UNIQUE INDEX uk_apertura_abierta ON aperturas_caja((cierre_id IS NULL)) WHERE cierre_id IS NULL;

INSERT INTO autonumerador (entidad, valor) VALUES ('APERTURACAJA', 0);

-- El cierre guarda con que saldo arranco el turno, para poder reconstruir el
-- arqueo despues: efectivo_teorico = saldo_inicial + ingresos - egresos.
ALTER TABLE cierres_caja ADD COLUMN saldo_inicial NUMERIC(15, 2) NOT NULL DEFAULT 0;
