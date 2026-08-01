-- La moneda de cada documento.
--
-- Hasta acá el dinero era un NUMERIC suelto y la moneda estaba implícita: PYG
-- escrito a mano en el XML del SIFEN y en el KuDE, y supuesto en todo lo demás.
-- Mientras haya una sola moneda eso funciona; el problema es el día que no,
-- porque entonces hay que tocar cada columna monetaria y cada cálculo a la vez.
--
-- DÓNDE VA LA COLUMNA
--
-- En el documento, no en cada importe. Una factura entera está en una moneda:
-- no existe una con el subtotal en guaraníes y el total en dólares. Guardar la
-- moneda por columna repetiría el mismo dato decenas de veces por factura y,
-- peor, permitiría escribir ese estado imposible.
--
-- Por eso las tablas de detalle (pedido_detalles, nota_credito_detalles) no
-- llevan columna: sus importes están en la moneda de su documento padre.
--
-- POR QUÉ LAS COLUMNAS DE IMPORTE SIGUEN SIENDO NUMERIC(15,2)
--
-- El guaraní no tiene centavos, así que podrían ser NUMERIC(15,0). Se dejan con
-- dos decimales a propósito: son las mismas columnas que tendrían que alojar un
-- importe en dólares. Quien redondea es el tipo Dinero, según la moneda; el
-- ancho de la columna solo tiene que alcanzar.
--
-- Todo lo que ya existe es en guaraníes, así que el backfill es PYG y el
-- sistema sigue comportándose igual.

-- Tipo nativo, igual que estado_factura o tipo_pago: la base rechaza un código
-- que no exista en vez de aceptar cualquier cadena de tres letras.
CREATE TYPE moneda AS ENUM ('PYG', 'USD');

COMMENT ON TYPE moneda IS
    'Código ISO 4217. Es el mismo que el SIFEN espera en cMoneOpe. Para agregar una: ALTER TYPE moneda ADD VALUE.';

ALTER TABLE productos       ADD COLUMN moneda moneda NOT NULL DEFAULT 'PYG';
ALTER TABLE pedidos         ADD COLUMN moneda moneda NOT NULL DEFAULT 'PYG';
ALTER TABLE facturas        ADD COLUMN moneda moneda NOT NULL DEFAULT 'PYG';
ALTER TABLE notas_credito   ADD COLUMN moneda moneda NOT NULL DEFAULT 'PYG';
ALTER TABLE pagos           ADD COLUMN moneda moneda NOT NULL DEFAULT 'PYG';
ALTER TABLE movimiento_caja ADD COLUMN moneda moneda NOT NULL DEFAULT 'PYG';
ALTER TABLE cierres_caja    ADD COLUMN moneda moneda NOT NULL DEFAULT 'PYG';
ALTER TABLE aperturas_caja  ADD COLUMN moneda moneda NOT NULL DEFAULT 'PYG';

COMMENT ON COLUMN productos.moneda IS
    'Moneda del precio de lista.';
COMMENT ON COLUMN facturas.moneda IS
    'Moneda de la factura, congelada al emitirla: va en el cMoneOpe del Documento Electrónico.';
COMMENT ON COLUMN movimiento_caja.moneda IS
    'Moneda del movimiento. Un arqueo solo puede sumar movimientos de una misma moneda.';
