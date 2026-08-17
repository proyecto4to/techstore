-- Identifica los datos de muestra que históricamente se cargaban junto con el
-- esquema. No se eliminan aquí: las instalaciones locales existentes deben
-- conservarlos. El perfil prod decide explícitamente si rechaza esa base o si
-- la provisiona como catálogo limpio mediante APP_DEMO_DATA_POLICY=purge.

ALTER TABLE productos
    ADD COLUMN es_demo BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE proveedores
    ADD COLUMN es_demo BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE marcas
    ADD COLUMN es_demo BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE productos
SET es_demo = TRUE
WHERE tenant_id = 1
  AND nombre IN (
      'Intel Core i9-13900K',
      'NVIDIA RTX 4090',
      'Samsung 1TB SSD',
      'Kingston 64GB RAM',
      'AMD Ryzen 9 7950X',
      'ASUS ROG Strix B650-A',
      'LG UltraGear 27" 144Hz',
      'Logitech G Pro Teclado',
      'Logitech G502 HERO',
      'Noctua NH-D15',
      'Fractal Design North',
      'WD Blue 4TB',
      'Redragon Kumara K552',
      'Genius KB-125',
      'Redragon M711 Cobra',
      'Genius DX-110',
      'HyperX Cloud Stinger',
      'JBL Tune 510BT',
      'Logitech C920 HD Pro',
      'JBL Go 3',
      'Kingston DataTraveler 64GB',
      'SanDisk Ultra 128GB',
      'Kingston A400 480GB',
      'Kingston Fury Beast 16GB',
      'Corsair CV550',
      'TP-Link Archer C6',
      'TP-Link TL-SG108',
      'Epson EcoTank L3250',
      'Epson T544 Tinta Negra',
      'Trust Hub USB 4 puertos',
      'Cable HDMI 2.0 2m',
      'APC Back-UPS 650VA',
      'Redragon P016 Mousepad XL',
      'Trust Base Refrigerante',
      'Cougar Armor One',
      'HP Mochila Notebook 15.6"'
  );

UPDATE proveedores
SET es_demo = TRUE
WHERE tenant_id = 1
  AND ruc IN (
      '80011111-1', '80022222-2', '80033333-3',
      '80044444-4', '80055555-5', '80066666-6'
  );

UPDATE marcas
SET es_demo = TRUE
WHERE tenant_id = 1
  AND nombre IN (
      'Intel', 'NVIDIA', 'Samsung', 'Corsair', 'Kingston',
      'AMD', 'ASUS', 'LG', 'Logitech', 'Noctua',
      'Fractal Design', 'Western Digital', 'HyperX', 'Redragon',
      'Genius', 'TP-Link', 'Epson', 'HP', 'JBL', 'Trust',
      'SanDisk', 'APC', 'Cougar'
  );

CREATE INDEX idx_productos_demo
    ON productos (tenant_id, es_demo) WHERE es_demo;
CREATE INDEX idx_proveedores_demo
    ON proveedores (tenant_id, es_demo) WHERE es_demo;
CREATE INDEX idx_marcas_demo
    ON marcas (tenant_id, es_demo) WHERE es_demo;
