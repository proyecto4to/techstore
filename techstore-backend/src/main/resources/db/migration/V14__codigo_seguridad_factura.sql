-- El codigo de seguridad (dCodSeg) forma parte del CDC, que es el identificador
-- unico del Documento Electronico. Se generaba al vuelo en cada descarga, asi que
-- una misma factura salia con un CDC distinto en el panel, en "Mis pedidos" y en
-- el correo. Ahora se fija una sola vez, al emitir la factura.
ALTER TABLE facturas ADD COLUMN codigo_seguridad VARCHAR(9);

-- Las facturas ya emitidas reciben un codigo estable (aleatorio por fila) para que
-- de ahora en mas su CDC deje de cambiar entre descargas.
UPDATE facturas
   SET codigo_seguridad = LPAD(((RANDOM() * 899999999)::BIGINT + 100000000)::TEXT, 9, '0')
 WHERE codigo_seguridad IS NULL;

ALTER TABLE facturas ALTER COLUMN codigo_seguridad SET NOT NULL;
