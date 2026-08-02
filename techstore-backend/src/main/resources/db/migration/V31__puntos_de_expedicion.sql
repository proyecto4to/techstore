-- Varios puntos de expedición.
--
-- Hasta acá había uno solo, en la configuración de la empresa, y todas las
-- facturas salían con el mismo prefijo 001-001. Un negocio con dos cajas, o con
-- una sucursal, necesita que cada una emita su propia serie.
--
-- CÓMO SE DETECTA DESDE DÓNDE SE EMITE
--
-- Por el usuario que factura. Cada cajero pertenece a un punto de expedición y
-- emite desde ahí. Es determinista y queda auditado: la factura dice quién la
-- hizo y desde dónde.
--
-- La alternativa —detectar por IP o por terminal— es frágil: con DHCP, wifi o
-- una notebook que se mueve, el mismo equipo cambia de dirección y las facturas
-- saldrían con el prefijo equivocado. Un número de timbrado mal asignado no se
-- corrige: se anula el documento.
--
-- CADA PUNTO LLEVA SU PROPIA NUMERACIÓN
--
-- 001-001-0000001 y 001-002-0000001 son dos documentos distintos y válidos: la
-- serie es independiente por punto. Por eso el autonumerador pasa a llevar una
-- entrada por punto (entidad 'FACTURA-001-002', por ejemplo).
--
-- El punto por defecto sigue usando la entidad 'FACTURA' de siempre, sin
-- sufijo, para que la numeración que ya viene corriendo continúe donde está en
-- vez de reiniciarse en 1.

CREATE TABLE puntos_expedicion (
    id             BIGSERIAL PRIMARY KEY,
    tenant_id      BIGINT      NOT NULL DEFAULT 1 REFERENCES tenants(id),
    -- VARCHAR y no CHAR: CHAR rellena con espacios y además Hibernate lo
    -- reporta como bpchar, que no valida contra el String de la entidad.
    establecimiento VARCHAR(3) NOT NULL CHECK (establecimiento ~ '^\d{3}$'),
    punto          VARCHAR(3)  NOT NULL CHECK (punto ~ '^\d{3}$'),
    nombre         VARCHAR(100) NOT NULL,
    activo         BOOLEAN     NOT NULL DEFAULT TRUE,
    -- El que se usa cuando quien emite no tiene uno asignado. Exactamente uno
    -- por empresa, garantizado por el índice único parcial de abajo.
    por_defecto    BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT punto_expedicion_unico UNIQUE (tenant_id, establecimiento, punto)
);

CREATE UNIQUE INDEX idx_punto_expedicion_por_defecto
    ON puntos_expedicion (tenant_id) WHERE por_defecto;

COMMENT ON TABLE puntos_expedicion IS
    'Cajas o sucursales que emiten documentos. Cada una lleva su propia serie de numeración.';
COMMENT ON COLUMN puntos_expedicion.por_defecto IS
    'El que se usa si quien emite no tiene punto asignado. Usa el autonumerador sin sufijo, o sea la serie histórica.';

-- El punto que ya venía usándose queda como el por defecto, con los valores de
-- la configuración de la empresa. Así nada cambia para quien ya está facturando.
INSERT INTO puntos_expedicion (tenant_id, establecimiento, punto, nombre, por_defecto)
SELECT 1, '001', '001', 'Casa central', TRUE
WHERE NOT EXISTS (SELECT 1 FROM puntos_expedicion WHERE tenant_id = 1);

-- Quién emite desde dónde.
ALTER TABLE usuarios
    ADD COLUMN punto_expedicion_id BIGINT REFERENCES puntos_expedicion(id);

COMMENT ON COLUMN usuarios.punto_expedicion_id IS
    'Desde qué punto emite este usuario. Nulo = el punto por defecto de la empresa.';

-- Mismo aislamiento por empresa que el resto del dominio (patrón de V24).
ALTER TABLE puntos_expedicion ENABLE ROW LEVEL SECURITY;
ALTER TABLE puntos_expedicion FORCE ROW LEVEL SECURITY;

CREATE POLICY aislamiento_tenant ON puntos_expedicion
    USING (tenant_id = tenant_actual())
    WITH CHECK (tenant_id = tenant_actual());

DO $$
BEGIN
    EXECUTE format($f$
        CREATE POLICY mantenimiento ON puntos_expedicion
            TO %I
            USING (tenant_actual() IS NULL)
            WITH CHECK (tenant_actual() IS NULL)
    $f$, current_user);
END $$;
