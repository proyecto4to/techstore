-- La dirección de entrega pasa a nacer de un punto en el mapa.
--
-- Antes se cargaba con doce campos escritos a mano, dos de ellos «Latitud» y
-- «Longitud», que nadie sabe de memoria y quedaban casi siempre vacíos. Ahora
-- se marca en el mapa y el texto de la calle se deduce del punto, así que:
--
--   * `descripcion` es lo único que el mapa no puede saber: cómo le dice la
--     persona a ese lugar —«Casa», «Trabajo», «Depósito»—. Es lo que se lee en
--     la lista, en vez de repetir la calle.
--   * departamento, ciudad y la primera línea dejan de ser obligatorios. Se
--     completan solos desde el punto, pero si el servicio de direcciones no
--     contesta, la ubicación igual queda guardada: las coordenadas son la
--     fuente principal y el texto es una comodidad derivada.
--
-- Nada se borra ni se reescribe: las direcciones existentes conservan lo que
-- tienen, y las nuevas simplemente pueden traer menos texto.

ALTER TABLE direcciones_entrega ADD COLUMN IF NOT EXISTS descripcion VARCHAR(60);

ALTER TABLE direcciones_entrega ALTER COLUMN departamento DROP NOT NULL;
ALTER TABLE direcciones_entrega ALTER COLUMN ciudad DROP NOT NULL;
ALTER TABLE direcciones_entrega ALTER COLUMN direccion_linea_1 DROP NOT NULL;
