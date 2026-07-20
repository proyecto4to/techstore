-- Notas de crédito parciales: una factura puede tener varias NC (se
-- devuelven productos/unidades por separado) y cada NC lleva su detalle.
ALTER TABLE notas_credito DROP CONSTRAINT IF EXISTS uk_nc_factura;

CREATE TABLE nota_credito_detalles (
    id BIGSERIAL PRIMARY KEY,
    nota_credito_id BIGINT NOT NULL,
    producto_id BIGINT NOT NULL,
    cantidad INTEGER NOT NULL,
    precio_unitario NUMERIC(12, 2) NOT NULL,
    subtotal NUMERIC(15, 2) NOT NULL,
    iva_aplicado NUMERIC(3, 0) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ncd_nota FOREIGN KEY (nota_credito_id) REFERENCES notas_credito(id) ON DELETE CASCADE,
    CONSTRAINT fk_ncd_producto FOREIGN KEY (producto_id) REFERENCES productos(id)
);

CREATE INDEX idx_ncd_nota ON nota_credito_detalles(nota_credito_id);
CREATE INDEX idx_ncd_producto ON nota_credito_detalles(producto_id);
