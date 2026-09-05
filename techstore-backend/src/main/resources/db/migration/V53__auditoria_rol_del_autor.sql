-- El rol de quien hizo la acción, guardado en el propio registro de auditoría.
--
-- Hasta acá el rol solo se podía deducir uniendo con `usuarios`, y eso responde
-- «qué rol tiene hoy», no «con qué rol hizo esto». Si a un cajero se le da
-- administración más adelante, todo su historial pasaría a leerse como si
-- siempre hubiera sido administrador. Un registro de auditoría tiene que
-- conservar lo que era cierto cuando se escribió.
--
-- Las filas viejas se rellenan con el rol actual del usuario, que es lo mejor
-- disponible: el dato histórico no existe y no hay forma de reconstruirlo. Van
-- marcadas por eso mismo —quedan con el rol de hoy—, así que para auditorías
-- finas solo son confiables los registros posteriores a esta migración.

ALTER TABLE audit_log ADD COLUMN usuario_rol VARCHAR(50);

UPDATE audit_log a
SET usuario_rol = r.nombre
FROM usuarios u
JOIN roles r ON r.id = u.rol_id
WHERE a.usuario_id = u.id
  AND a.usuario_rol IS NULL;

CREATE INDEX idx_audit_log_rol ON audit_log (tenant_id, usuario_rol, fecha DESC);
CREATE INDEX idx_audit_log_accion ON audit_log (tenant_id, accion, fecha DESC);
