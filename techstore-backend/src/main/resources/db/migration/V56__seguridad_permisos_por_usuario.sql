-- Permisos individuales, roles administrables y auditoría con resultado.
--
-- Lo que ya existía (V48) queda intacto: la matriz por rol sigue siendo la
-- base y nadie cambia de permisos por correr esta migración. Acá se agrega la
-- excepción por persona, que es lo que faltaba: «este cajero además exporta»,
-- «a este otro no le dejes eliminar», sin inventar un rol nuevo para cada caso
-- ni aflojar el rol entero.
--
-- Cómo se combina, y es una sola regla para todos los módulos:
--
--     anotación del backend  (piso: si niega, no hay nada que discutir)
--       AND
--     restricción individual (permitido = FALSE gana sobre todo lo demás)
--       si no,
--     concesión individual   (permitido = TRUE)
--       si no,
--     permiso del rol        (V48; su ausencia sigue significando permitido)
--
-- La prioridad es deliberada: lo que niega gana. Una restricción puesta a una
-- persona es una decisión tomada sobre esa persona, y el rol no puede
-- deshacerla por venir después.

CREATE TABLE permisos_usuario (
    id         BIGSERIAL PRIMARY KEY,
    tenant_id  BIGINT NOT NULL REFERENCES tenants(id),
    usuario_id BIGINT NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    modulo     VARCHAR(40) NOT NULL,
    accion     VARCHAR(20) NOT NULL,
    permitido  BOOLEAN NOT NULL,
    -- Por qué se hizo la excepción. Dentro de seis meses, «no» sin motivo es
    -- indistinguible de un error de tipeo.
    motivo     VARCHAR(200),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT permisos_usuario_unico UNIQUE (tenant_id, usuario_id, modulo, accion)
);

COMMENT ON TABLE permisos_usuario IS
    'Excepciones por persona sobre la matriz del rol. Sin fila, manda el rol. Una fila en FALSE niega aunque el rol permita.';
COMMENT ON COLUMN permisos_usuario.permitido IS
    'TRUE concede (siempre dentro de lo que la anotación del backend ya permite); FALSE restringe y gana sobre el rol.';

CREATE INDEX idx_permisos_usuario_consulta ON permisos_usuario (tenant_id, usuario_id, modulo);

-- Aislamiento por empresa, mismo patrón que V24 y V48.
ALTER TABLE permisos_usuario ENABLE ROW LEVEL SECURITY;
ALTER TABLE permisos_usuario FORCE ROW LEVEL SECURITY;

CREATE POLICY aislamiento_tenant ON permisos_usuario
    USING (tenant_id = tenant_actual())
    WITH CHECK (tenant_id = tenant_actual());

DO $$
BEGIN
    EXECUTE format($f$
        CREATE POLICY mantenimiento ON permisos_usuario
            TO %I
            USING (tenant_actual() IS NULL)
            WITH CHECK (tenant_actual() IS NULL)
    $f$, current_user);
END $$;

-- ---------------------------------------------------------------------------
-- Roles que se pueden dar de baja sin borrarlos.
--
-- Borrar un rol rompería el historial: las facturas y la auditoría guardan con
-- qué rol se hizo cada cosa. Desactivarlo lo saca de las listas de asignación y
-- deja quieto lo ya ocurrido. Arranca en TRUE, así que ningún rol cambia hoy.
-- ---------------------------------------------------------------------------
ALTER TABLE roles ADD COLUMN activo BOOLEAN NOT NULL DEFAULT TRUE;

COMMENT ON COLUMN roles.activo IS
    'Un rol inactivo no se puede asignar a nadie más; quienes ya lo tienen siguen trabajando igual.';

-- ---------------------------------------------------------------------------
-- Auditoría: en qué terminó la operación y desde dónde se hizo.
--
-- Las dos columnas van nulas para todo lo ya registrado y para todo lo que se
-- audite sin informarlas: no se inventa un pasado que nadie observó. Un intento
-- rechazado es justamente lo que más importa mirar, y hasta ahora no se
-- guardaba ninguno.
-- ---------------------------------------------------------------------------
ALTER TABLE audit_log ADD COLUMN resultado  VARCHAR(20);
ALTER TABLE audit_log ADD COLUMN plataforma VARCHAR(20);

COMMENT ON COLUMN audit_log.resultado IS
    'Permitido o Rechazado. Nulo en los registros anteriores a esta columna.';
COMMENT ON COLUMN audit_log.plataforma IS
    'Web, Movil o Api, según lo declara el cliente. Nulo si no se pudo determinar.';

-- Lo que se consulta en la pantalla de auditoría de seguridad: los rechazos,
-- los más nuevos primero.
CREATE INDEX idx_audit_log_resultado ON audit_log (tenant_id, resultado, fecha DESC);
