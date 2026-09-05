-- Cajero y repartidor pueden iniciar una conversación con administración.
--
-- V48 les dio VER y EDITAR sobre CONVERSACIONES, que alcanzaba para leer y
-- responder: mandar un mensaje es un POST sobre un sub-recurso y el interceptor
-- lo trata como EDITAR. Abrir la conversación, en cambio, es un POST sobre la
-- raíz del módulo, o sea CREAR, y sin este permiso responde 403.
--
-- El cliente ya lo tenía, porque «Atención al Cliente» siempre se abrió así.

UPDATE permisos_rol p
SET permitido = TRUE, updated_at = CURRENT_TIMESTAMP
FROM roles r
WHERE p.rol_id = r.id
  AND r.nombre IN ('Cajero', 'Repartidor')
  AND p.modulo = 'CONVERSACIONES'
  AND p.accion = 'CREAR';

-- Por si la fila no existiera en una base anterior a la siembra de V48.
INSERT INTO permisos_rol (tenant_id, rol_id, modulo, accion, permitido)
SELECT t.id, r.id, 'CONVERSACIONES', 'CREAR', TRUE
FROM tenants t
CROSS JOIN roles r
WHERE r.nombre IN ('Cajero', 'Repartidor')
ON CONFLICT (tenant_id, rol_id, modulo, accion) DO UPDATE
    SET permitido = TRUE, updated_at = CURRENT_TIMESTAMP;
