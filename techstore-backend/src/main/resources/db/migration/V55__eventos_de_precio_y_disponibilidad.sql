-- El flujo de eventos distingue qué cambió del producto.
--
-- Hasta ahora, tocar un producto emitía siempre `PRODUCT_UPDATED`, y solo el
-- stock tenía un evento propio. Con eso alcanza para refrescar la pantalla
-- —cualquier cambio la vuelve a pedir—, pero no para reaccionar a algo puntual:
-- avisarle a quien lo tenía en favoritos que volvió a haber, o que bajó de
-- precio, obliga a comparar contra lo anterior, y eso ya no está.
--
-- Los tres eventos nuevos salen de comparar OLD contra NEW, que es lo único que
-- el disparador sabe y nadie más:
--
--   PRICE_UPDATED           el precio cambió, con el anterior y el nuevo
--   PRODUCT_OUT_OF_STOCK    quedó sin unidades disponibles
--   PRODUCT_BACK_IN_STOCK   volvió a haber
--
-- «Disponible» es el stock real menos el comprometido: lo que se puede vender
-- hoy. Un producto con diez unidades y diez reservadas está agotado para quien
-- entra a comprar, aunque en el depósito estén.
--
-- Se emiten además del `PRODUCT_UPDATED` de siempre, no en su lugar: quien solo
-- quiere refrescar la lista sigue mirando ese, sin enterarse de nada nuevo.

CREATE OR REPLACE FUNCTION enqueue_domain_event() RETURNS trigger AS $$
DECLARE
    row_data JSONB;
    tenant BIGINT;
    aggregate BIGINT;
    action VARCHAR(20);
    antes JSONB;
    ahora JSONB;
    disponible_antes INTEGER;
    disponible_ahora INTEGER;
BEGIN
    row_data := CASE WHEN TG_OP = 'DELETE' THEN to_jsonb(OLD) ELSE to_jsonb(NEW) END;
    tenant := (row_data ->> 'tenant_id')::BIGINT;
    -- Algunas tablas centrales (por ejemplo configuracion) usan una clave
    -- compuesta y no poseen una columna id. El evento sigue siendo válido y
    -- provoca la reconsulta de la colección completa.
    aggregate := COALESCE((row_data ->> 'id')::BIGINT, 0);
    action := CASE TG_OP WHEN 'INSERT' THEN 'CREATED' WHEN 'UPDATE' THEN 'UPDATED' ELSE 'DELETED' END;

    INSERT INTO domain_event_outbox(tenant_id, event_type, aggregate_type, aggregate_id, payload)
    VALUES (tenant, TG_ARGV[0] || '_' || action, TG_ARGV[0], aggregate,
            jsonb_build_object('operation', TG_OP));

    IF TG_TABLE_NAME = 'productos' AND TG_OP = 'UPDATE' THEN
        antes := to_jsonb(OLD);
        ahora := to_jsonb(NEW);

        IF (antes ->> 'stock_real') IS DISTINCT FROM (ahora ->> 'stock_real')
           OR (antes ->> 'stock_comprometido') IS DISTINCT FROM (ahora ->> 'stock_comprometido') THEN
            INSERT INTO domain_event_outbox(tenant_id, event_type, aggregate_type, aggregate_id, payload)
            VALUES (tenant, 'STOCK_UPDATED', 'PRODUCT', aggregate,
                    jsonb_build_object('stockReal', ahora -> 'stock_real',
                                       'stockComprometido', ahora -> 'stock_comprometido'));

            disponible_antes := COALESCE((antes ->> 'stock_real')::INTEGER, 0)
                              - COALESCE((antes ->> 'stock_comprometido')::INTEGER, 0);
            disponible_ahora := COALESCE((ahora ->> 'stock_real')::INTEGER, 0)
                              - COALESCE((ahora ->> 'stock_comprometido')::INTEGER, 0);

            IF disponible_antes > 0 AND disponible_ahora <= 0 THEN
                INSERT INTO domain_event_outbox(tenant_id, event_type, aggregate_type, aggregate_id, payload)
                VALUES (tenant, 'PRODUCT_OUT_OF_STOCK', 'PRODUCT', aggregate,
                        jsonb_build_object('disponible', disponible_ahora));
            ELSIF disponible_antes <= 0 AND disponible_ahora > 0 THEN
                INSERT INTO domain_event_outbox(tenant_id, event_type, aggregate_type, aggregate_id, payload)
                VALUES (tenant, 'PRODUCT_BACK_IN_STOCK', 'PRODUCT', aggregate,
                        jsonb_build_object('disponible', disponible_ahora));
            END IF;
        END IF;

        IF (antes ->> 'precio') IS DISTINCT FROM (ahora ->> 'precio') THEN
            INSERT INTO domain_event_outbox(tenant_id, event_type, aggregate_type, aggregate_id, payload)
            VALUES (tenant, 'PRICE_UPDATED', 'PRODUCT', aggregate,
                    jsonb_build_object('precioAnterior', antes -> 'precio',
                                       'precio', ahora -> 'precio'));
        END IF;
    END IF;

    RETURN CASE WHEN TG_OP = 'DELETE' THEN OLD ELSE NEW END;
END;
$$ LANGUAGE plpgsql;
