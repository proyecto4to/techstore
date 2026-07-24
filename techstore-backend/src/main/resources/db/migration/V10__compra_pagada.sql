-- El pago de una compra a proveedor es un paso aparte de la recepción de la
-- mercadería: recibir suma stock; pagar registra el egreso en caja. La bandera
-- 'pagada' marca las compras ya pagadas (las ventas no la usan: quedan en false).
ALTER TABLE pedidos ADD COLUMN pagada BOOLEAN NOT NULL DEFAULT FALSE;
