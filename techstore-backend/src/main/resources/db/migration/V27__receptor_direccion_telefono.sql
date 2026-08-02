-- Dirección y teléfono del receptor.
--
-- El KuDE los listaba con una raya fija porque el dato no existía en ningún
-- lado. Ahora se pueden cargar al cobrar y quedan congelados en la factura,
-- igual que el RUC, el nombre y el correo: si el cliente después corrige sus
-- datos, la factura ya emitida no cambia.
--
-- Son opcionales a propósito. El manual técnico los da como ocurrencia 0-1
-- (dDirRec D213, dTelRec D214) y solo exige la dirección para exportación o
-- nota de remisión. Exigirlos en el mostrador frenaría una venta al contado
-- por un dato que el fisco no pide.

ALTER TABLE facturas ADD COLUMN receptor_direccion VARCHAR(255);
ALTER TABLE facturas ADD COLUMN receptor_telefono  VARCHAR(20);

COMMENT ON COLUMN facturas.receptor_direccion IS
    'Dirección del receptor al emitir. Nulo = no se informó; el KuDE entonces no imprime la línea.';
COMMENT ON COLUMN facturas.receptor_telefono IS
    'Teléfono del receptor al emitir. Nulo = no se informó.';
