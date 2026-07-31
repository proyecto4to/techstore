-- La tabla audit_log existe desde V1 pero ningún código escribió nunca en ella.
-- Se la ajusta para poder usarla de verdad.

-- No toda acción auditable tiene un usuario autenticado detrás (una tarea del
-- sistema) ni apunta a una fila concreta (guardar la configuración de la
-- empresa). Antes que perder el registro, se admite dejar el dato en nulo.
ALTER TABLE audit_log ALTER COLUMN usuario_id DROP NOT NULL;
ALTER TABLE audit_log ALTER COLUMN entidad_id DROP NOT NULL;

-- Resumen legible de lo que pasó, para no tener que interpretar el JSON a ojo
-- en la pantalla de auditoría.
ALTER TABLE audit_log ADD COLUMN detalle VARCHAR(500);

-- El email se copia al momento del hecho. Si mañana se borra o se renombra al
-- usuario, el rastro de lo que hizo tiene que seguir diciendo quién fue.
ALTER TABLE audit_log ADD COLUMN usuario_email VARCHAR(255);

-- La consulta más frecuente sobre una auditoría es "qué hizo esta persona".
CREATE INDEX idx_audit_log_usuario ON audit_log(usuario_id, fecha DESC);

COMMENT ON TABLE audit_log IS
    'Rastro de las acciones sensibles: quién, qué, cuándo y con qué valores. Solo se inserta; no se actualiza ni se borra.';
COMMENT ON COLUMN audit_log.cambios IS
    'Valores antes/después por campo: {"precio": {"antes": 100, "despues": 120}}';
