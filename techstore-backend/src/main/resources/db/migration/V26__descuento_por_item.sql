-- Descuento por ítem.
--
-- El KuDE ya imprimía una columna "Descuento", pero con un 0 fijo: la columna
-- estaba porque el formato oficial la exige, no porque el sistema supiera
-- hacer descuentos. Esto le pone el dato atrás.
--
-- POR QUÉ ES SOBRE EL PRECIO UNITARIO Y NO SOBRE LA LÍNEA
--
-- Lo define el manual técnico del SIFEN, campo EA002 (dDescItem): "Descuento
-- particular sobre el precio unitario por ítem (incluidos impuestos)". Y el
-- total del ítem (EA008) se calcula:
--
--   (precio unitario − descuento particular − descuento global
--    − anticipo particular − anticipo global) × cantidad
--
-- Así que guardar el descuento de la línea entera obligaría a dividirlo por la
-- cantidad al emitir el documento electrónico, con el redondeo que eso arrastra.
-- Se guarda como lo pide el documento: por unidad.
--
-- Los descuentos global y los anticipos no se implementan todavía; sus campos
-- van en 0 en el XML, que es lo que el manual pide cuando no existen.

ALTER TABLE pedido_detalles
    ADD COLUMN descuento NUMERIC(12, 2) NOT NULL DEFAULT 0;

ALTER TABLE nota_credito_detalles
    ADD COLUMN descuento NUMERIC(12, 2) NOT NULL DEFAULT 0;

COMMENT ON COLUMN pedido_detalles.descuento IS
    'Descuento por unidad, no por línea (EA002 del SIFEN). El subtotal ya lo tiene descontado.';
COMMENT ON COLUMN nota_credito_detalles.descuento IS
    'El mismo descuento que tenía la línea del pedido: lo devuelto es lo que se cobró.';

-- Un descuento negativo sería un recargo encubierto, y uno mayor al precio
-- daría una línea de importe negativo. Ninguno de los dos puede existir.
ALTER TABLE pedido_detalles
    ADD CONSTRAINT pedido_detalles_descuento_valido
        CHECK (descuento >= 0 AND descuento <= precio_unitario);

ALTER TABLE nota_credito_detalles
    ADD CONSTRAINT nota_credito_detalles_descuento_valido
        CHECK (descuento >= 0 AND descuento <= precio_unitario);
