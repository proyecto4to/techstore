-- Comprobantes que adjunta quien paga por transferencia.
--
-- Cuando el pedido se paga transfiriendo, la tienda necesita ver el respaldo
-- para confirmarlo. Hasta ahora eso se resolvía por fuera del sistema —una foto
-- por WhatsApp— y quedaba desligado del pedido.
--
-- Es una tabla y no una columna porque son varios archivos por pedido: quien
-- transfiere en dos veces tiene dos respaldos, y quien manda la boleta y el
-- resumen tiene dos imágenes de la misma operación. El tope de tres vive en el
-- servicio, que es donde se puede explicar el motivo al rechazar.

CREATE TABLE pago_comprobantes (
    id                   BIGSERIAL PRIMARY KEY,
    tenant_id            BIGINT NOT NULL REFERENCES tenants(id),
    pedido_id            BIGINT NOT NULL REFERENCES pedidos(id) ON DELETE CASCADE,
    archivo_nombre       VARCHAR(255) NOT NULL,
    archivo_content_type VARCHAR(100) NOT NULL,
    tamano_bytes         BIGINT NOT NULL,
    subido_por           BIGINT REFERENCES usuarios(id),
    created_at           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE pago_comprobantes IS
    'Imágenes que respaldan el pago de un pedido. Hasta tres por pedido; el tope se valida en el servicio.';

COMMENT ON COLUMN pago_comprobantes.archivo_nombre IS
    'Nombre del archivo en el directorio de subidas, con UUID: nunca el nombre que traía el dispositivo.';

CREATE INDEX idx_pago_comprobantes_pedido ON pago_comprobantes (tenant_id, pedido_id);

-- Mismo aislamiento por empresa que el resto del dominio (patrón de V24).
ALTER TABLE pago_comprobantes ENABLE ROW LEVEL SECURITY;
ALTER TABLE pago_comprobantes FORCE ROW LEVEL SECURITY;

CREATE POLICY aislamiento_tenant ON pago_comprobantes
    USING (tenant_id = tenant_actual())
    WITH CHECK (tenant_id = tenant_actual());

DO $$
BEGIN
    EXECUTE format($f$
        CREATE POLICY mantenimiento ON pago_comprobantes
            TO %I
            USING (tenant_actual() IS NULL)
            WITH CHECK (tenant_actual() IS NULL)
    $f$, current_user);
END $$;
