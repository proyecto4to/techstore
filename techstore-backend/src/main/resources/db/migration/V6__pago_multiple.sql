-- Un pedido puede pagarse con más de un método (efectivo + transferencia).
-- Se libera la unicidad de pedido_id en pagos: ahora hay una fila por método.
ALTER TABLE pagos DROP CONSTRAINT IF EXISTS pagos_pedido_id_key;

CREATE INDEX IF NOT EXISTS idx_pagos_pedido ON pagos(pedido_id);
