-- Catálogo ampliado: marcas y productos adicionales con fotos reales
-- (créditos en techstore-frontend/public/img/productos/CREDITS.md)

INSERT INTO marcas (nombre, descripcion) VALUES
    ('AMD', 'Procesadores y tarjetas gráficas'),
    ('ASUS', 'Placas madre y periféricos'),
    ('LG', 'Monitores y pantallas'),
    ('Logitech', 'Periféricos'),
    ('Noctua', 'Refrigeración'),
    ('Fractal Design', 'Gabinetes'),
    ('Western Digital', 'Almacenamiento');

INSERT INTO productos (nombre, descripcion, precio, iva, stock_real, marca_id, imagen_url) VALUES
    ('AMD Ryzen 9 7950X', 'Procesador de 16 núcleos y 32 hilos, socket AM5', 4200000.00, 10, 6,
        (SELECT id FROM marcas WHERE nombre = 'AMD'), '/img/productos/ryzen.jpg'),
    ('ASUS ROG Strix B650-A', 'Placa madre ATX socket AM5 con WiFi 6E', 1850000.00, 10, 7,
        (SELECT id FROM marcas WHERE nombre = 'ASUS'), '/img/productos/mobo.jpg'),
    ('LG UltraGear 27" 144Hz', 'Monitor gaming QHD IPS de 27 pulgadas', 2300000.00, 10, 9,
        (SELECT id FROM marcas WHERE nombre = 'LG'), '/img/productos/monitor.jpg'),
    ('Logitech G Pro Teclado', 'Teclado mecánico compacto para esports', 780000.00, 10, 14,
        (SELECT id FROM marcas WHERE nombre = 'Logitech'), '/img/productos/teclado.jpg'),
    ('Logitech G502 HERO', 'Mouse gaming de 25.600 DPI con 11 botones programables', 350000.00, 10, 25,
        (SELECT id FROM marcas WHERE nombre = 'Logitech'), '/img/productos/mouse.jpg'),
    ('Noctua NH-D15', 'Cooler de CPU de doble torre, ultra silencioso', 690000.00, 10, 11,
        (SELECT id FROM marcas WHERE nombre = 'Noctua'), '/img/productos/cooler.jpg'),
    ('Fractal Design North', 'Gabinete ATX con frente de madera y panel mesh', 950000.00, 10, 8,
        (SELECT id FROM marcas WHERE nombre = 'Fractal Design'), '/img/productos/gabinete.jpg'),
    ('WD Blue 4TB', 'Disco duro de 4TB, 5400 rpm, SATA III', 620000.00, 10, 18,
        (SELECT id FROM marcas WHERE nombre = 'Western Digital'), '/img/productos/hdd.jpg');
