-- Facturacion a contribuyente. Hasta ahora el receptor del Documento
-- Electronico estaba fijo como consumidor final (iTipIDRec=5, dNumIDRec=0), asi
-- que no habia forma de emitir una factura al RUC del cliente.

-- Datos fiscales del cliente, para no tener que dictarlos en cada compra.
ALTER TABLE usuarios ADD COLUMN ruc VARCHAR(20);
ALTER TABLE usuarios ADD COLUMN razon_social VARCHAR(255);

-- El receptor se congela en la factura al emitirla: si el cliente despues
-- corrige su RUC o su razon social, las facturas ya emitidas no cambian.
-- receptor_ruc nulo = se emitio a consumidor final.
ALTER TABLE facturas ADD COLUMN receptor_ruc VARCHAR(20);
ALTER TABLE facturas ADD COLUMN receptor_nombre VARCHAR(255);
