-- Qué proveedor ofrece qué producto, y a qué precio.
--
-- Es la pieza que faltaba para comparar antes de comprar: hasta acá el sistema
-- solo conocía UN costo por producto (el de la última compra recibida), sin
-- recordar de quién fue ni qué cobraban los demás.
--
-- La tabla se alimenta sola: cada vez que se recibe una compra, queda asentado
-- que ese proveedor tiene ese producto y a ese precio. También se puede cargar
-- a mano, para registrar que un proveedor lo ofrece antes de haberle comprado
-- nunca (una cotización que pasaron por teléfono, por ejemplo).
--
-- La rapidez de entrega y la fiabilidad NO se guardan acá: se calculan del
-- historial de pedidos de compra cada vez que se comparan. Un número guardado
-- envejece; el historial no.

CREATE TABLE proveedor_productos (
    id          BIGSERIAL PRIMARY KEY,
    tenant_id   BIGINT       NOT NULL DEFAULT 1 REFERENCES tenants(id),
    proveedor_id BIGINT      NOT NULL REFERENCES proveedores(id),
    producto_id BIGINT       NOT NULL REFERENCES productos(id),
    moneda      moneda       NOT NULL DEFAULT 'PYG',
    precio      NUMERIC(12, 2) NOT NULL CHECK (precio > 0),
    -- El proveedor puede quedarse sin stock sin dejar de "tener" el producto:
    -- la oferta se apaga en vez de borrarse, y conserva el precio de referencia.
    disponible  BOOLEAN      NOT NULL DEFAULT TRUE,
    actualizado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Un proveedor tiene un solo precio vigente por producto (por empresa).
    CONSTRAINT proveedor_producto_unico UNIQUE (tenant_id, proveedor_id, producto_id)
);

COMMENT ON TABLE proveedor_productos IS
    'Oferta vigente de cada proveedor por producto. Se actualiza sola al recibir compras; también admite carga manual.';
COMMENT ON COLUMN proveedor_productos.precio IS
    'Último precio conocido de ese proveedor para ese producto.';
COMMENT ON COLUMN proveedor_productos.disponible IS
    'FALSE = el proveedor no lo tiene ahora; la fila queda como referencia de precio.';

CREATE INDEX idx_proveedor_productos_producto ON proveedor_productos (producto_id);

-- El mismo aislamiento por empresa que el resto del dominio (patrón de V24):
-- la política corta por tenant, y la de mantenimiento deja pasar solo al
-- dueño de la tabla (Flyway) cuando no hay tenant fijado.
ALTER TABLE proveedor_productos ENABLE ROW LEVEL SECURITY;
ALTER TABLE proveedor_productos FORCE ROW LEVEL SECURITY;

CREATE POLICY aislamiento_tenant ON proveedor_productos
    USING (tenant_id = tenant_actual())
    WITH CHECK (tenant_id = tenant_actual());

DO $$
BEGIN
    EXECUTE format($f$
        CREATE POLICY mantenimiento ON proveedor_productos
            TO %I
            USING (tenant_actual() IS NULL)
            WITH CHECK (tenant_actual() IS NULL)
    $f$, current_user);
END $$;
