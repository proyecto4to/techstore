-- Configuración editable desde la app (clave/valor). Se usa para el SMTP del
-- envío de correos, así se puede configurar desde Admin -> Correos sin tocar
-- archivos. (App local de un solo negocio: la clave se guarda tal cual.)
CREATE TABLE configuracion (
    clave VARCHAR(60) PRIMARY KEY,
    valor TEXT,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO configuracion (clave, valor) VALUES
    ('smtp.host', 'smtp.gmail.com'),
    ('smtp.port', '587'),
    ('smtp.username', ''),
    ('smtp.password', ''),
    ('smtp.from', '');
