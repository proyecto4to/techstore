-- Costo del producto y visibilidad en la tienda.
--
-- COSTO
--
-- El sistema sabía a cuánto vende cada producto pero no a cuánto lo compra, así
-- que no podía decir cuánto gana. El costo se toma de la última compra recibida
-- de ese producto: es el precio al que hoy habría que reponerlo, que es el que
-- sirve para decidir a cuánto venderlo.
--
-- Se guarda la fecha porque un costo sin fecha no se puede interpretar: no es
-- lo mismo uno de la semana pasada que uno de hace dos años.
--
-- Nulo = todavía no se recibió ninguna compra de ese producto. Se deja nulo a
-- propósito en vez de 0: un costo de cero diría que el producto es gratis y
-- daría márgenes del 100%.

ALTER TABLE productos ADD COLUMN costo NUMERIC(12, 2);
ALTER TABLE productos ADD COLUMN costo_actualizado_en TIMESTAMP;

COMMENT ON COLUMN productos.costo IS
    'Costo unitario de la última compra recibida. Nulo mientras no se haya comprado nunca.';
COMMENT ON COLUMN productos.costo_actualizado_en IS
    'Cuándo se recibió esa compra. Un costo sin fecha no se puede interpretar.';

-- VISIBILIDAD
--
-- Un producto sin stock no se muestra en la tienda: ofrecer algo que no se
-- puede entregar hace perder la venta dos veces, la del producto y la
-- confianza. Pero sigue existiendo en el catálogo interno, porque hay que
-- poder comprarlo de nuevo.
--
-- Es una preferencia por producto y no una regla global: un producto que se
-- fabrica a pedido puede querer mostrarse sin stock.
ALTER TABLE productos
    ADD COLUMN ocultar_sin_stock BOOLEAN NOT NULL DEFAULT TRUE;

COMMENT ON COLUMN productos.ocultar_sin_stock IS
    'Si en TRUE, el producto desaparece de la tienda cuando su stock disponible llega a 0.';
