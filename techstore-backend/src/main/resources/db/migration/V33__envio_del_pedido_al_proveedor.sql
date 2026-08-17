-- Mandarle el pedido al proveedor.
--
-- Hasta acá una compra se cargaba en el sistema y el pedido se le pasaba al
-- proveedor por afuera —un llamado, un mensaje suelto—, así que no quedaba
-- registro de qué se le pidió ni de cuándo. Ahora el sistema lo manda y lo
-- anota.
--
-- POR QUÉ NO ES UN ESTADO DEL PEDIDO
--
-- Podría parecer que "enviado" va en `estado`, entre Pendiente y Confirmado.
-- No: mandar el pedido no es que el proveedor lo haya aceptado. La compra
-- sigue Pendiente hasta que llega la mercadería, y eso no cambia porque el
-- correo haya salido. Son dos cosas distintas y se guardan aparte.
--
-- EL CANAL SE GUARDA AUNQUE HOY SOLO FUNCIONE UNO
--
-- El pedido era poder elegir correo, WhatsApp o los dos. WhatsApp necesita una
-- cuenta de WhatsApp Business API con un proveedor habilitado (Meta Cloud API,
-- Twilio), verificación del negocio y costo por conversación: no se puede
-- mandar desde un WhatsApp común. El canal se modela completo igual, para que
-- el día que esa cuenta exista sea agregar el envío y no migrar la tabla.

CREATE TYPE canal_envio AS ENUM ('Correo', 'WhatsApp', 'Ambos');

ALTER TABLE pedidos
    ADD COLUMN enviado_en    TIMESTAMP,
    ADD COLUMN enviado_canal canal_envio;

COMMENT ON COLUMN pedidos.enviado_en IS
    'Cuándo se le mandó el pedido al proveedor. Nulo = todavía no se mandó. Reenviar lo pisa.';
COMMENT ON COLUMN pedidos.enviado_canal IS
    'Por dónde se mandó. Solo aplica a compras.';
