-- Visibilidad del producto en la aplicación de clientes.
--
-- Ya existían dos banderas, pero ninguna sirve para esto:
--   activo           el producto deja de operar en todo el sistema, tampoco se
--                    puede vender en el mostrador;
--   ocultar_sin_stock lo esconde solo mientras no hay unidades, y vuelve solo.
--
-- Lo que faltaba es poder sacar un producto de la vidriera sin sacarlo del
-- negocio: sigue existiendo, sigue en el inventario y se sigue pudiendo vender
-- por caja, pero no aparece en el catálogo ni en la búsqueda de la app.

ALTER TABLE productos
    ADD COLUMN visible_en_app BOOLEAN NOT NULL DEFAULT TRUE;

COMMENT ON COLUMN productos.visible_en_app IS
    'Si el producto se ofrece en la app de clientes. En falso sigue existiendo y se puede vender por mostrador, pero no aparece en el catálogo publico, la busqueda ni el detalle.';

-- El catalogo publico filtra por esta columna en cada consulta.
CREATE INDEX idx_productos_visible_en_app ON productos(visible_en_app);
