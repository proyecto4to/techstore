-- Correo del receptor de la factura. Hace falta para la venta en mostrador: el
-- pedido queda a nombre del cajero, asi que sin esto la factura se le mandaba a
-- el y no al cliente que compro. Tambien alimenta el dEmailRec del Documento
-- Electronico, que por el mismo motivo salia con el correo del cajero.
--
-- Nulo = no se informo correo; la factura se manda al dueño del pedido, como
-- hacen las ventas web.
ALTER TABLE facturas ADD COLUMN receptor_email VARCHAR(255);
