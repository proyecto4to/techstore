-- El cierre sumaba los movimientos por rango de fechas (desde el cierre anterior
-- hasta "ahora"). Si la transaccion de un cobro se confirmaba justo despues de
-- que corriera un cierre, ese movimiento no lo veia; y como el turno siguiente
-- arrancaba en la fecha de ese cierre, su fecha ya era anterior y no entraba en
-- ningun arqueo. Plata que desaparecia del cierre.
--
-- Ahora cada cierre RECLAMA los movimientos que todavia no fueron arqueados
-- (cierre_id nulo), sin depender de fechas: si uno se confirma tarde, queda
-- pendiente y lo toma el cierre siguiente.
ALTER TABLE movimiento_caja ADD COLUMN cierre_id BIGINT;
ALTER TABLE movimiento_caja
    ADD CONSTRAINT fk_movimiento_cierre FOREIGN KEY (cierre_id) REFERENCES cierres_caja(id);

-- Los movimientos historicos quedan asignados al primer cierre que los cubrio,
-- para que los cierres ya registrados sigan reflejando lo mismo que antes.
UPDATE movimiento_caja m
   SET cierre_id = (SELECT c.id
                      FROM cierres_caja c
                     WHERE c.fecha_cierre >= m.fecha
                     ORDER BY c.fecha_cierre
                     LIMIT 1)
 WHERE EXISTS (SELECT 1 FROM cierres_caja c WHERE c.fecha_cierre >= m.fecha);

-- El cierre solo consulta los pendientes: indice parcial sobre esa condicion.
CREATE INDEX idx_movimiento_pendiente ON movimiento_caja(cierre_id) WHERE cierre_id IS NULL;

-- Los egresos por transferencia (p. ej. pagos a proveedor) no se informaban en
-- el cierre, asi que el movimiento bancario del turno quedaba incompleto.
ALTER TABLE cierres_caja ADD COLUMN egresos_transferencia NUMERIC(15, 2) NOT NULL DEFAULT 0;
