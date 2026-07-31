-- Punto de reposición por producto. En 0 el producto no avisa nunca, que es el
-- comportamiento que había hasta ahora, así que los existentes no cambian.
ALTER TABLE productos ADD COLUMN stock_minimo INTEGER NOT NULL DEFAULT 0;

ALTER TABLE productos ADD CONSTRAINT chk_productos_stock_minimo CHECK (stock_minimo >= 0);

COMMENT ON COLUMN productos.stock_minimo IS
    'Cantidad a partir de la cual el producto aparece en el aviso de reposición. 0 = sin aviso.';
