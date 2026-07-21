-- Bandeja de salida de notificaciones por correo (factura, cambios de clave, etc.).
-- Todo correo queda registrado; se "envía" de verdad solo si el SMTP está
-- configurado y habilitado, si no queda como Simulado (demostrativo, local).
CREATE TABLE correos (
    id BIGSERIAL PRIMARY KEY,
    tipo VARCHAR(30) NOT NULL,
    destinatario VARCHAR(150) NOT NULL,
    destinatario_nombre VARCHAR(200),
    asunto VARCHAR(255) NOT NULL,
    cuerpo TEXT NOT NULL,
    referencia VARCHAR(50),
    estado VARCHAR(20) NOT NULL,
    error TEXT,
    tiene_adjunto BOOLEAN NOT NULL DEFAULT FALSE,
    adjunto_nombre VARCHAR(150),
    fecha TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_correos_fecha ON correos(fecha);
