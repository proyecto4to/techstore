-- El pago por transferencia pasa a verificarse dentro del sistema.
--
-- Hasta ahora el cliente adjuntaba el comprobante y nada más: nadie lo
-- aprobaba ni lo rechazaba, y un pedido a domicilio podía salir sin cobrar,
-- pagándole en efectivo al repartidor. La regla nueva del negocio es que todo
-- pedido a domicilio se paga antes, por transferencia, y que el pago tiene que
-- quedar verificado por la tienda antes de preparar nada.
--
-- El estado del pago va aparte del estado del pedido a propósito. El enum
-- estado_pedido (P, C, G, N, R, X) lo leen los reportes, la web, la app de
-- GeneXus y la facturación; meterle «en verificación» en el medio obligaba a
-- tocarlos a todos. Así, un pedido sigue Pendiente mientras se revisa el
-- comprobante, y al aprobarse pasa a Confirmado y Pagado como cualquier cobro.

-- 1. Estado del pago del pedido.
--
-- NO_REQUERIDO: se cobra en persona (retiro con pago en el local, o pedidos
-- viejos con efectivo al repartidor). PENDIENTE_COMPROBANTE: eligió
-- transferencia y todavía no mandó el respaldo. EN_VERIFICACION: hay un
-- comprobante esperando que la tienda lo mire. APROBADO: la tienda confirmó
-- que la plata llegó. RECHAZADO: la tienda no lo pudo validar; el cliente
-- puede mandar otro.
ALTER TABLE pedidos
    ADD COLUMN estado_pago VARCHAR(30) NOT NULL DEFAULT 'NO_REQUERIDO',
    ADD COLUMN pago_rechazo_motivo VARCHAR(500),
    ADD COLUMN pago_verificado_por BIGINT REFERENCES usuarios(id),
    ADD COLUMN pago_verificado_en TIMESTAMP;

ALTER TABLE pedidos
    ADD CONSTRAINT ck_pedidos_estado_pago CHECK (estado_pago IN (
        'NO_REQUERIDO', 'PENDIENTE_COMPROBANTE', 'EN_VERIFICACION', 'APROBADO', 'RECHAZADO'));

COMMENT ON COLUMN pedidos.estado_pago IS
    'Verificación del pago por transferencia, aparte de estado: un pedido sigue Pendiente mientras se revisa el comprobante.';
COMMENT ON COLUMN pedidos.pago_rechazo_motivo IS
    'Por qué la tienda no validó el último comprobante. Lo lee el cliente para corregirlo.';

-- Los pedidos por transferencia que ya existían quedan en el estado que de
-- verdad tienen: los cobrados, aprobados; los pendientes, según si llegaron a
-- mandar el comprobante. Los demás no se tocan.
UPDATE pedidos p
SET estado_pago = CASE
        WHEN p.estado IN ('G', 'N', 'R') THEN 'APROBADO'
        WHEN EXISTS (SELECT 1 FROM pago_comprobantes c WHERE c.pedido_id = p.id) THEN 'EN_VERIFICACION'
        ELSE 'PENDIENTE_COMPROBANTE'
    END
WHERE p.metodo_pago_checkout = 'TRANSFERENCIA_BANCARIA'
  AND p.estado <> 'X';

CREATE INDEX idx_pedidos_estado_pago ON pedidos (tenant_id, estado_pago)
    WHERE estado_pago IN ('EN_VERIFICACION', 'PENDIENTE_COMPROBANTE', 'RECHAZADO');

-- 2. Cada comprobante tiene su propia revisión.
--
-- Al rechazar, el comprobante no se borra: queda como respaldo de lo que pasó,
-- con el motivo. Por eso el tope de tres cuenta solo los que siguen en
-- revisión, y no los rechazados.
ALTER TABLE pago_comprobantes
    ADD COLUMN estado VARCHAR(20) NOT NULL DEFAULT 'EN_REVISION',
    ADD COLUMN motivo_rechazo VARCHAR(500),
    ADD COLUMN revisado_por BIGINT REFERENCES usuarios(id),
    ADD COLUMN revisado_en TIMESTAMP;

ALTER TABLE pago_comprobantes
    ADD CONSTRAINT ck_pago_comprobantes_estado CHECK (estado IN ('EN_REVISION', 'APROBADO', 'RECHAZADO'));

UPDATE pago_comprobantes c
SET estado = 'APROBADO'
FROM pedidos p
WHERE p.id = c.pedido_id AND p.estado IN ('G', 'N', 'R');

-- 3. El comprobante se sube antes de que exista el pedido.
--
-- El cliente transfiere, adjunta el respaldo y recién ahí confirma la compra:
-- el pedido nace ya en verificación, y no queda ninguno a domicilio esperando
-- un comprobante que nunca llega. Mientras no se usa en un checkout, el
-- archivo es solo de quien lo subió (subido_por) y no tiene pedido.
ALTER TABLE pago_comprobantes ALTER COLUMN pedido_id DROP NOT NULL;

COMMENT ON COLUMN pago_comprobantes.pedido_id IS
    'Nulo mientras el comprobante espera el checkout que lo va a usar. Los que quedan sueltos más de un día se borran solos.';

CREATE INDEX idx_pago_comprobantes_sueltos ON pago_comprobantes (tenant_id, subido_por)
    WHERE pedido_id IS NULL;

-- 4. Historial del pedido.
--
-- La auditoría registra quién hizo qué, pero es para la administración y no se
-- le muestra al cliente. Esto es la línea de tiempo del propio pedido: cuándo
-- se recibió, cuándo llegó el comprobante, quién lo aprobó o por qué se
-- rechazó. Solo se agregan filas; nunca se editan.
CREATE TABLE pedido_eventos (
    id          BIGSERIAL PRIMARY KEY,
    tenant_id   BIGINT NOT NULL REFERENCES tenants(id),
    pedido_id   BIGINT NOT NULL REFERENCES pedidos(id) ON DELETE CASCADE,
    tipo        VARCHAR(40) NOT NULL,
    detalle     VARCHAR(500),
    usuario_id  BIGINT REFERENCES usuarios(id),
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE pedido_eventos IS
    'Línea de tiempo de cada pedido (creación, comprobantes, verificación del pago, confirmación, cancelación). Solo se agregan filas.';

CREATE INDEX idx_pedido_eventos_pedido ON pedido_eventos (tenant_id, pedido_id, created_at);

ALTER TABLE pedido_eventos ENABLE ROW LEVEL SECURITY;
ALTER TABLE pedido_eventos FORCE ROW LEVEL SECURITY;

CREATE POLICY aislamiento_tenant ON pedido_eventos
    USING (tenant_id = tenant_actual())
    WITH CHECK (tenant_id = tenant_actual());

DO $$
BEGIN
    EXECUTE format($f$
        CREATE POLICY mantenimiento ON pedido_eventos
            TO %I
            USING (tenant_actual() IS NULL)
            WITH CHECK (tenant_actual() IS NULL)
    $f$, current_user);
END $$;

-- El pedido ya existente arranca su historial con lo que se sabe de él.
INSERT INTO pedido_eventos (tenant_id, pedido_id, tipo, detalle, created_at)
SELECT p.tenant_id, p.id, 'CREADO', 'Pedido recibido', COALESCE(p.created_at, p.fecha)
FROM pedidos p
WHERE p.tipo = 'Venta';
