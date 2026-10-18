-- Promociones: un descuento con fecha de inicio y de fin que administración
-- enciende y apaga.
--
-- Decisiones que conviene entender antes de tocar esta tabla:
--
-- 1. PORCENTAJE Y NADA MÁS. Un descuento fijo en guaraníes obliga a decidir qué
--    pasa cuando supera el precio del producto, y esa pregunta aparece justo
--    cuando alguien cambia un precio meses después. El porcentaje se adapta
--    solo. El tope de 90% es para que un cero de más no regale el catálogo.
--
-- 2. EL ALCANCE ES UNO SOLO. O todos los productos, o un producto, o una marca.
--    Varias condiciones a la vez («esta marca pero solo estos dos productos»)
--    se arman con dos promociones, y la regla de abajo resuelve cuál manda.
--
-- 3. SI SE PISAN, GANA LA MEJOR PARA QUIEN COMPRA, y nunca se suman. Sumar dos
--    veces 60% deja el producto gratis; elegir el mayor descuento es lo que la
--    gente espera y no depende del orden en que se cargaron.
--
-- 4. LA VENTANA ES DE FECHA Y HORA. «Del viernes al domingo» se carga con hora
--    de inicio y de fin; el servidor compara contra su propio reloj.
--
-- 5. ACTIVA ES UN INTERRUPTOR, no la vigencia. Una promoción puede estar activa
--    y todavía no haber empezado. Apagarla no la borra: las promociones viejas
--    quedan para saber por qué un pedido de marzo salió más barato.
--
-- El descuento aplicado no se guarda acá: va en pedido_detalle.descuento, la
-- misma columna que usa el descuento manual de la venta directa. Así la
-- factura ya lo muestra y un pedido viejo conserva el precio que tuvo, aunque
-- después la promoción se apague o se edite.

CREATE TABLE promociones (
    id          BIGSERIAL PRIMARY KEY,
    tenant_id   BIGINT NOT NULL REFERENCES tenants(id),
    nombre      VARCHAR(120) NOT NULL,
    porcentaje  NUMERIC(5, 2) NOT NULL,
    alcance     VARCHAR(20) NOT NULL,
    producto_id BIGINT REFERENCES productos(id),
    marca_id    BIGINT REFERENCES marcas(id),
    desde       TIMESTAMP NOT NULL,
    hasta       TIMESTAMP NOT NULL,
    activa      BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP,

    CONSTRAINT promociones_porcentaje_valido CHECK (porcentaje > 0 AND porcentaje <= 90),
    CONSTRAINT promociones_ventana_valida CHECK (hasta > desde),
    CONSTRAINT promociones_alcance_valido CHECK (alcance IN ('TODOS', 'PRODUCTO', 'MARCA')),
    -- Cada alcance trae exactamente la referencia que le corresponde: una
    -- promoción de marca sin marca no se puede resolver, y una de «todos» con
    -- un producto cargado miente sobre lo que hace.
    CONSTRAINT promociones_referencia_coherente CHECK (
        (alcance = 'TODOS' AND producto_id IS NULL AND marca_id IS NULL)
        OR (alcance = 'PRODUCTO' AND producto_id IS NOT NULL AND marca_id IS NULL)
        OR (alcance = 'MARCA' AND marca_id IS NOT NULL AND producto_id IS NULL)
    )
);

-- La consulta caliente es siempre la misma: las vigentes de esta empresa en
-- este momento. Entra por tenant y activa, y descarta por fechas.
CREATE INDEX idx_promociones_vigentes ON promociones (tenant_id, activa, desde, hasta);
CREATE INDEX idx_promociones_producto ON promociones (producto_id) WHERE producto_id IS NOT NULL;
CREATE INDEX idx_promociones_marca ON promociones (marca_id) WHERE marca_id IS NOT NULL;

-- Encender una promoción cambia el precio de la vidriera sin tocar la tabla de
-- productos, así que el disparador de `productos` no se entera y las apps
-- abiertas seguirían mostrando el precio viejo hasta que alguien recargue.
-- Con esto, la promoción viaja por el mismo flujo de eventos que el resto.
CREATE TRIGGER trg_sync_promociones
    AFTER INSERT OR UPDATE OR DELETE ON promociones
    FOR EACH ROW EXECUTE FUNCTION enqueue_domain_event('PROMOTION');
