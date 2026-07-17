-- Imagen del producto: URL relativa (assets del frontend) o absoluta (CDN externo).
ALTER TABLE productos ADD COLUMN imagen_url VARCHAR(500);

UPDATE productos SET imagen_url = '/img/productos/cpu.svg' WHERE nombre ILIKE '%core i9%';
UPDATE productos SET imagen_url = '/img/productos/gpu.svg' WHERE nombre ILIKE '%rtx%';
UPDATE productos SET imagen_url = '/img/productos/ssd.svg' WHERE nombre ILIKE '%ssd%';
UPDATE productos SET imagen_url = '/img/productos/ram.svg' WHERE nombre ILIKE '%ram%';
UPDATE productos SET imagen_url = '/img/productos/psu.svg' WHERE nombre ILIKE '%rm850%';
