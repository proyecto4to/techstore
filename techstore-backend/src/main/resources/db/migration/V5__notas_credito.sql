-- Notas de crédito: devolución total de un pedido facturado.
CREATE TABLE notas_credito (
    id BIGSERIAL PRIMARY KEY,
    numero VARCHAR(20) NOT NULL UNIQUE,
    factura_id BIGINT NOT NULL,
    pedido_id BIGINT NOT NULL,
    motivo VARCHAR(255) NOT NULL,
    subtotal NUMERIC(15, 2) NOT NULL,
    iva NUMERIC(15, 2) NOT NULL,
    total NUMERIC(15, 2) NOT NULL,
    usuario_id BIGINT NOT NULL,
    fecha TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_nc_factura FOREIGN KEY (factura_id) REFERENCES facturas(id),
    CONSTRAINT fk_nc_pedido FOREIGN KEY (pedido_id) REFERENCES pedidos(id),
    CONSTRAINT fk_nc_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id),
    CONSTRAINT uk_nc_factura UNIQUE (factura_id)
);

CREATE INDEX idx_nc_fecha ON notas_credito(fecha);
