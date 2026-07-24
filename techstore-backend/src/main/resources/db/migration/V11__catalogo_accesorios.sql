-- Catálogo ampliado a accesorios informáticos en general (no solo componentes de PC)
-- y más proveedores. Las imágenes usan el placeholder por defecto salvo donde
-- reutilizamos una foto real ya existente; el frontend cae al default.svg solo.

-- Marcas nuevas (idempotente: no duplica las ya existentes).
INSERT INTO marcas (nombre, descripcion) VALUES
    ('Kingston', 'Memorias y almacenamiento'),
    ('Corsair', 'Periféricos, memorias y fuentes'),
    ('HyperX', 'Auriculares y periféricos gaming'),
    ('Redragon', 'Periféricos gaming accesibles'),
    ('Genius', 'Periféricos y accesorios'),
    ('TP-Link', 'Redes y conectividad'),
    ('Epson', 'Impresión'),
    ('HP', 'Impresión e informática'),
    ('JBL', 'Audio'),
    ('Trust', 'Accesorios informáticos'),
    ('SanDisk', 'Almacenamiento portátil'),
    ('APC', 'Energía y respaldo'),
    ('Cougar', 'Sillas y muebles gamer')
ON CONFLICT (nombre) DO NOTHING;

-- Productos: accesorios variados (periféricos, audio, redes, impresión, energía, etc.).
INSERT INTO productos (nombre, descripcion, precio, iva, stock_real, marca_id, imagen_url) VALUES
    ('Redragon Kumara K552', 'Teclado mecánico gaming TKL con switches azules y retroiluminación', 250000.00, 10, 30,
        (SELECT id FROM marcas WHERE nombre = 'Redragon'), '/img/productos/teclado.jpg'),
    ('Genius KB-125', 'Teclado USB estándar en español, resistente a salpicaduras', 65000.00, 10, 60,
        (SELECT id FROM marcas WHERE nombre = 'Genius'), NULL),
    ('Redragon M711 Cobra', 'Mouse gaming RGB de 10.000 DPI con 7 botones', 130000.00, 10, 45,
        (SELECT id FROM marcas WHERE nombre = 'Redragon'), '/img/productos/mouse.jpg'),
    ('Genius DX-110', 'Mouse óptico USB de 1000 DPI, uso general', 45000.00, 10, 80,
        (SELECT id FROM marcas WHERE nombre = 'Genius'), '/img/productos/mouse.jpg'),
    ('HyperX Cloud Stinger', 'Auriculares gaming con micrófono, almohadillas de espuma viscoelástica', 420000.00, 10, 25,
        (SELECT id FROM marcas WHERE nombre = 'HyperX'), NULL),
    ('JBL Tune 510BT', 'Auriculares inalámbricos Bluetooth con 40h de batería', 380000.00, 10, 20,
        (SELECT id FROM marcas WHERE nombre = 'JBL'), NULL),
    ('Logitech C920 HD Pro', 'Webcam Full HD 1080p con micrófono estéreo', 620000.00, 10, 18,
        (SELECT id FROM marcas WHERE nombre = 'Logitech'), NULL),
    ('JBL Go 3', 'Parlante Bluetooth portátil resistente al agua IP67', 290000.00, 10, 35,
        (SELECT id FROM marcas WHERE nombre = 'JBL'), NULL),
    ('Kingston DataTraveler 64GB', 'Pendrive USB 3.2 de 64 GB', 65000.00, 10, 120,
        (SELECT id FROM marcas WHERE nombre = 'Kingston'), NULL),
    ('SanDisk Ultra 128GB', 'Memoria microSD 128 GB clase 10 con adaptador', 110000.00, 10, 90,
        (SELECT id FROM marcas WHERE nombre = 'SanDisk'), NULL),
    ('Kingston A400 480GB', 'Disco sólido SSD SATA de 480 GB', 380000.00, 10, 40,
        (SELECT id FROM marcas WHERE nombre = 'Kingston'), '/img/productos/ssd.jpg'),
    ('Kingston Fury Beast 16GB', 'Memoria RAM DDR4 16 GB 3200 MHz', 340000.00, 10, 50,
        (SELECT id FROM marcas WHERE nombre = 'Kingston'), '/img/productos/ram.jpg'),
    ('Corsair CV550', 'Fuente de poder 550W 80 Plus Bronze', 480000.00, 10, 22,
        (SELECT id FROM marcas WHERE nombre = 'Corsair'), '/img/productos/psu.jpg'),
    ('TP-Link Archer C6', 'Router WiFi doble banda AC1200 con 4 antenas', 320000.00, 10, 28,
        (SELECT id FROM marcas WHERE nombre = 'TP-Link'), NULL),
    ('TP-Link TL-SG108', 'Switch de red Gigabit de 8 puertos', 260000.00, 10, 24,
        (SELECT id FROM marcas WHERE nombre = 'TP-Link'), NULL),
    ('Epson EcoTank L3250', 'Impresora multifunción con sistema de tinta continua y WiFi', 1450000.00, 10, 12,
        (SELECT id FROM marcas WHERE nombre = 'Epson'), NULL),
    ('Epson T544 Tinta Negra', 'Botella de tinta original 65 ml para EcoTank', 60000.00, 10, 100,
        (SELECT id FROM marcas WHERE nombre = 'Epson'), NULL),
    ('Trust Hub USB 4 puertos', 'Hub USB 3.0 de 4 puertos con cable de 80 cm', 95000.00, 10, 70,
        (SELECT id FROM marcas WHERE nombre = 'Trust'), NULL),
    ('Cable HDMI 2.0 2m', 'Cable HDMI 4K de 2 metros', 45000.00, 10, 150,
        (SELECT id FROM marcas WHERE nombre = 'Trust'), NULL),
    ('APC Back-UPS 650VA', 'UPS de 650VA / 390W con 6 tomas', 720000.00, 10, 15,
        (SELECT id FROM marcas WHERE nombre = 'APC'), NULL),
    ('Redragon P016 Mousepad XL', 'Alfombrilla gamer extendida 800x300 mm', 90000.00, 10, 60,
        (SELECT id FROM marcas WHERE nombre = 'Redragon'), NULL),
    ('Trust Base Refrigerante', 'Base refrigerante para notebook con 2 ventiladores', 160000.00, 10, 40,
        (SELECT id FROM marcas WHERE nombre = 'Trust'), NULL),
    ('Cougar Armor One', 'Silla gamer ergonómica reclinable', 2100000.00, 10, 8,
        (SELECT id FROM marcas WHERE nombre = 'Cougar'), NULL),
    ('HP Mochila Notebook 15.6"', 'Mochila para notebook hasta 15.6 pulgadas con bolsillos', 230000.00, 10, 35,
        (SELECT id FROM marcas WHERE nombre = 'HP'), NULL);

-- Más proveedores (RUCs propios para no chocar con los cargados desde la app).
INSERT INTO proveedores (razon_social, nombre_fantasia, email, telefono, ciudad, ruc) VALUES
    ('Insumos Informáticos del Este SA', 'InfoEste', 'ventas@infoeste.com.py', '(061) 500-100', 'Ciudad del Este', '80011111-1'),
    ('Mayorista Tech Paraguay SRL', 'MayTech', 'compras@maytech.com.py', '(021) 600-200', 'Asunción', '80022222-2'),
    ('ImportPC SA', 'ImportPC', 'info@importpc.com.py', '(021) 610-300', 'Asunción', '80033333-3'),
    ('Periféricos y Más SRL', 'PeriMás', 'contacto@perimas.com.py', '(021) 620-400', 'Fernando de la Mora', '80044444-4'),
    ('RedeTel Distribuciones SA', 'RedeTel', 'ventas@redetel.com.py', '(021) 630-500', 'Lambaré', '80055555-5'),
    ('OfiPrint Insumos SRL', 'OfiPrint', 'ventas@ofiprint.com.py', '(021) 640-600', 'San Lorenzo', '80066666-6')
ON CONFLICT (ruc) DO NOTHING;
