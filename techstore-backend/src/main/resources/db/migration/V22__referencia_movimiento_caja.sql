-- La referencia del movimiento era ambigua: las facturas y las notas de crédito
-- tienen numeradores independientes pero el mismo formato 001-001-NNNNNNN, así
-- que el cobro de la factura 6 y la devolución de la nota de crédito 6 quedaban
-- con la referencia idéntica y no había manera de saber cuál era cuál.
--
-- Desde ahora el código antepone el tipo de documento. Acá se corrige lo ya
-- guardado, que sí se puede desambiguar por el concepto: los movimientos
-- automáticos lo escriben con un texto fijo.

UPDATE movimiento_caja
   SET referencia = 'FAC ' || referencia
 WHERE concepto LIKE 'Cobro pedido%'
   AND referencia IS NOT NULL
   AND referencia NOT LIKE 'FAC %';

UPDATE movimiento_caja
   SET referencia = 'NC ' || referencia
 WHERE concepto LIKE 'Devolución pedido%'
   AND referencia IS NOT NULL
   AND referencia NOT LIKE 'NC %';

-- Los pagos a proveedores ya usaban el prefijo COMPRA-, y los movimientos
-- cargados a mano llevan la referencia que escribió la persona: no se tocan.

COMMENT ON COLUMN movimiento_caja.referencia IS
    'Documento que originó el movimiento, con el tipo adelante (FAC / NC / COMPRA-). Sin el prefijo la referencia es ambigua entre facturas y notas de crédito.';
