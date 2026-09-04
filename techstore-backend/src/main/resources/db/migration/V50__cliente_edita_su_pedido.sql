-- El cliente puede operar sobre su propio pedido.
--
-- Corrige una regresión de V48: al aplicar los permisos por rol, cancelar el
-- propio pedido pasó a responder 403, porque el verbo es PUT y el rol Cliente
-- no tenía PEDIDOS/EDITAR. Lo mismo bloqueaba adjuntar y quitar los
-- comprobantes del pago.
--
-- El permiso es del módulo, pero lo que protege el pedido ajeno no es la falta
-- de este permiso sino la comprobación de dueño que hace el servicio: un
-- cliente con PEDIDOS/EDITAR sigue recibiendo 403 si toca un pedido que no es
-- suyo.
--
-- Va en una migración nueva y no editando V48 porque V48 ya se aplicó: cambiar
-- una migración ejecutada rompe la validación de Flyway por checksum, que es
-- exactamente lo que pasó al intentarlo.

UPDATE permisos_rol p
SET permitido = TRUE, updated_at = CURRENT_TIMESTAMP
FROM roles r
WHERE p.rol_id = r.id
  AND r.nombre = 'Cliente'
  AND p.modulo = 'PEDIDOS'
  AND p.accion = 'EDITAR';

-- Por si la fila no existiera —una base que se creó antes de la siembra—.
INSERT INTO permisos_rol (tenant_id, rol_id, modulo, accion, permitido)
SELECT t.id, r.id, 'PEDIDOS', 'EDITAR', TRUE
FROM tenants t
CROSS JOIN roles r
WHERE r.nombre = 'Cliente'
ON CONFLICT (tenant_id, rol_id, modulo, accion) DO UPDATE
    SET permitido = TRUE, updated_at = CURRENT_TIMESTAMP;
