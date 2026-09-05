-- Conversaciones privadas entre dos personas, además del soporte al cliente.
--
-- Hasta acá una conversación tenía un único `cliente_id` y todo el staff la
-- veía. Eso alcanza para «Atención al Cliente», pero no para que un cajero o un
-- repartidor le escriba a administración sin que el resto mire.
--
-- El modelo pasa a tener dos partes:
--   * `iniciador_id`  — quien la abrió. Es el `cliente_id` de antes, renombrado
--                       porque ya no siempre es un cliente.
--   * `destinatario_id` — con quién habla. NULL significa «con la tienda», que
--                       es exactamente el caso de soporte que ya funcionaba.
--
-- Renombrar en lugar de agregar una columna nueva mantiene los datos y los
-- índices existentes, y evita dejar dos columnas que signifiquen lo mismo.

ALTER TABLE conversaciones RENAME COLUMN cliente_id TO iniciador_id;

ALTER TABLE conversaciones
    ADD COLUMN destinatario_id BIGINT REFERENCES usuarios(id),
    ADD COLUMN tipo VARCHAR(20) NOT NULL DEFAULT 'SOPORTE';

ALTER TABLE conversaciones
    ADD CONSTRAINT ck_conversacion_tipo CHECK (tipo IN ('SOPORTE', 'PRIVADA'));

-- Una privada tiene siempre dos partes y son distintas; una de soporte no tiene
-- destinatario porque el otro extremo es la tienda, no una persona.
ALTER TABLE conversaciones
    ADD CONSTRAINT ck_conversacion_partes CHECK (
        (tipo = 'SOPORTE' AND destinatario_id IS NULL)
        OR (tipo = 'PRIVADA' AND destinatario_id IS NOT NULL
            AND destinatario_id <> iniciador_id));

-- «No crear conversaciones duplicadas si ya existe un chat entre ambos»: el par
-- se normaliza con LEAST/GREATEST para que dé igual quién la haya abierto.
CREATE UNIQUE INDEX uk_conversacion_privada_par
    ON conversaciones (tenant_id,
                       LEAST(iniciador_id, destinatario_id),
                       GREATEST(iniciador_id, destinatario_id))
    WHERE tipo = 'PRIVADA';

-- El índice de unicidad de soporte se rehace acotado a ese tipo: sin el filtro,
-- un usuario no podría tener a la vez su chat de soporte y una privada abierta.
DROP INDEX IF EXISTS uk_conversacion_abierta_contexto;
CREATE UNIQUE INDEX uk_conversacion_abierta_contexto
    ON conversaciones (tenant_id, iniciador_id, COALESCE(pedido_id, 0))
    WHERE estado = 'ABIERTA' AND tipo = 'SOPORTE';

-- Para listar rápido lo que le corresponde a cada uno.
CREATE INDEX idx_conversaciones_destinatario
    ON conversaciones (tenant_id, destinatario_id,
                       COALESCE(ultimo_mensaje_en, created_at) DESC);
