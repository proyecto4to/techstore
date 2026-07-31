-- El bloqueo por intentos fallidos vivía en memoria del proceso: cada reinicio
-- del backend lo borraba, así que bastaba con esperar (o provocar) un reinicio
-- para volver a tener los intentos limpios. Pasa a la base.
CREATE TABLE intento_login (
    email           VARCHAR(255) PRIMARY KEY,
    fallos          INTEGER   NOT NULL DEFAULT 0,
    bloqueado_hasta TIMESTAMP,
    ultimo_intento  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Para la limpieza periódica de registros viejos.
CREATE INDEX idx_intento_login_ultimo ON intento_login(ultimo_intento);

COMMENT ON TABLE intento_login IS
    'Intentos fallidos de login por email. Se borra la fila al primer ingreso correcto.';
