CREATE TYPE estado_pedido AS ENUM ('P', 'C', 'G', 'N', 'R', 'X');
CREATE TYPE tipo_pedido AS ENUM ('Venta', 'Compra');
CREATE TYPE tipo_movimiento AS ENUM ('Ingreso', 'Egreso');
CREATE TYPE tipo_pago AS ENUM ('Efectivo', 'Tarjeta', 'Transferencia', 'Cheque');
CREATE TYPE estado_caja AS ENUM ('A', 'C');
CREATE TYPE estado_factura AS ENUM ('Emitida', 'Pagada', 'Cancelada', 'Anulada');

CREATE TABLE roles (
    id BIGSERIAL PRIMARY KEY,
    nombre VARCHAR(50) NOT NULL UNIQUE,
    descripcion TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO roles (nombre, descripcion) VALUES
    ('Admin', 'Administrador del sistema'),
    ('Cajero', 'Operador de caja'),
    ('Cliente', 'Cliente de la tienda');

CREATE TABLE usuarios (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    nombre VARCHAR(100) NOT NULL,
    apellido VARCHAR(100) NOT NULL,
    rol_id BIGINT NOT NULL,
    activo BOOLEAN DEFAULT TRUE,
    last_login TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_usuario_rol FOREIGN KEY (rol_id) REFERENCES roles(id)
);

CREATE INDEX idx_usuarios_email ON usuarios(email);
CREATE INDEX idx_usuarios_rol_id ON usuarios(rol_id);
CREATE INDEX idx_usuarios_activo ON usuarios(activo);

CREATE TABLE clientes (
    id BIGSERIAL PRIMARY KEY,
    nombre VARCHAR(100) NOT NULL,
    apellido VARCHAR(100) NOT NULL,
    email VARCHAR(255),
    telefono VARCHAR(20),
    direccion TEXT,
    ciudad VARCHAR(100),
    pais VARCHAR(100) DEFAULT 'Paraguay',
    cedula VARCHAR(20) UNIQUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_clientes_email ON clientes(email);

CREATE TABLE proveedores (
    id BIGSERIAL PRIMARY KEY,
    razon_social VARCHAR(255) NOT NULL,
    nombre_fantasia VARCHAR(255),
    email VARCHAR(255),
    telefono VARCHAR(20),
    direccion TEXT,
    ciudad VARCHAR(100),
    pais VARCHAR(100) DEFAULT 'Paraguay',
    ruc VARCHAR(20) UNIQUE,
    activo BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE marcas (
    id BIGSERIAL PRIMARY KEY,
    nombre VARCHAR(100) NOT NULL UNIQUE,
    descripcion TEXT,
    activo BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE modelos (
    id BIGSERIAL PRIMARY KEY,
    nombre VARCHAR(100) NOT NULL,
    marca_id BIGINT NOT NULL,
    descripcion TEXT,
    activo BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_modelo_marca FOREIGN KEY (marca_id) REFERENCES marcas(id),
    CONSTRAINT uk_modelo_marca UNIQUE (nombre, marca_id)
);

CREATE TABLE productos (
    id BIGSERIAL PRIMARY KEY,
    nombre VARCHAR(255) NOT NULL,
    descripcion TEXT,
    precio NUMERIC(12, 2) NOT NULL,
    iva NUMERIC(3, 0) DEFAULT 10,
    stock_real INTEGER DEFAULT 0,
    stock_comprometido INTEGER DEFAULT 0,
    marca_id BIGINT,
    modelo_id BIGINT,
    activo BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_producto_marca FOREIGN KEY (marca_id) REFERENCES marcas(id),
    CONSTRAINT fk_producto_modelo FOREIGN KEY (modelo_id) REFERENCES modelos(id)
);

CREATE INDEX idx_productos_nombre ON productos(nombre);
CREATE INDEX idx_productos_activo ON productos(activo);

CREATE TABLE autonumerador (
    id BIGSERIAL PRIMARY KEY,
    entidad VARCHAR(50) NOT NULL UNIQUE,
    valor INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO autonumerador (entidad, valor) VALUES
    ('PEDIDO', 0),
    ('FACTURA', 0),
    ('MOVIMIENTOSTOCK', 0),
    ('PAGO', 0),
    ('CAJA', 0),
    ('NOTACREDITO', 0);

CREATE TABLE pedidos (
    id BIGSERIAL PRIMARY KEY,
    numero INTEGER NOT NULL UNIQUE,
    tipo tipo_pedido NOT NULL,
    cliente_id BIGINT,
    proveedor_id BIGINT,
    estado estado_pedido DEFAULT 'P',
    fecha TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    total NUMERIC(15, 2) DEFAULT 0,
    usuario_id BIGINT NOT NULL,
    notas TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_pedido_cliente FOREIGN KEY (cliente_id) REFERENCES clientes(id),
    CONSTRAINT fk_pedido_proveedor FOREIGN KEY (proveedor_id) REFERENCES proveedores(id),
    CONSTRAINT fk_pedido_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id)
);

CREATE INDEX idx_pedidos_estado ON pedidos(estado);
CREATE INDEX idx_pedidos_cliente_id ON pedidos(cliente_id);
CREATE INDEX idx_pedidos_tipo ON pedidos(tipo);
CREATE INDEX idx_pedidos_fecha ON pedidos(fecha);

CREATE TABLE pedido_detalles (
    id BIGSERIAL PRIMARY KEY,
    pedido_id BIGINT NOT NULL,
    producto_id BIGINT NOT NULL,
    cantidad INTEGER NOT NULL,
    precio_unitario NUMERIC(12, 2) NOT NULL,
    subtotal NUMERIC(15, 2) NOT NULL,
    iva_aplicado NUMERIC(3, 0) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_detalle_pedido FOREIGN KEY (pedido_id) REFERENCES pedidos(id) ON DELETE CASCADE,
    CONSTRAINT fk_detalle_producto FOREIGN KEY (producto_id) REFERENCES productos(id),
    CONSTRAINT uk_detalle_pedido_producto UNIQUE (pedido_id, producto_id)
);

CREATE TABLE pagos (
    id BIGSERIAL PRIMARY KEY,
    numero INTEGER NOT NULL UNIQUE,
    pedido_id BIGINT NOT NULL UNIQUE,
    monto NUMERIC(15, 2) NOT NULL,
    tipo_pago tipo_pago NOT NULL,
    numero_comprobante VARCHAR(50),
    fecha TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    usuario_id BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_pago_pedido FOREIGN KEY (pedido_id) REFERENCES pedidos(id),
    CONSTRAINT fk_pago_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id)
);

CREATE INDEX idx_pagos_pedido_id ON pagos(pedido_id);
CREATE INDEX idx_pagos_fecha ON pagos(fecha);

CREATE TABLE facturas (
    id BIGSERIAL PRIMARY KEY,
    numero VARCHAR(20) NOT NULL UNIQUE,
    tipo VARCHAR(10) NOT NULL,
    pedido_id BIGINT NOT NULL,
    fecha TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    subtotal NUMERIC(15, 2) NOT NULL,
    iva NUMERIC(15, 2) NOT NULL,
    total NUMERIC(15, 2) NOT NULL,
    estado estado_factura DEFAULT 'Emitida',
    usuario_id BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_factura_pedido FOREIGN KEY (pedido_id) REFERENCES pedidos(id),
    CONSTRAINT fk_factura_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id)
);

CREATE INDEX idx_facturas_numero ON facturas(numero);
CREATE INDEX idx_facturas_pedido_id ON facturas(pedido_id);
CREATE INDEX idx_facturas_estado ON facturas(estado);

CREATE TABLE movimiento_caja (
    id BIGSERIAL PRIMARY KEY,
    numero INTEGER NOT NULL UNIQUE,
    tipo tipo_movimiento NOT NULL,
    monto NUMERIC(15, 2) NOT NULL,
    concepto VARCHAR(255) NOT NULL,
    referencia VARCHAR(50),
    usuario_id BIGINT NOT NULL,
    fecha TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_movimiento_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id)
);

CREATE INDEX idx_movimiento_caja_fecha ON movimiento_caja(fecha);

CREATE TABLE audit_log (
    id BIGSERIAL PRIMARY KEY,
    entidad VARCHAR(100) NOT NULL,
    entidad_id BIGINT NOT NULL,
    accion VARCHAR(20) NOT NULL,
    usuario_id BIGINT NOT NULL,
    cambios JSONB,
    fecha TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_audit_log_entidad ON audit_log(entidad, entidad_id);
CREATE INDEX idx_audit_log_fecha ON audit_log(fecha);

-- Password: admin123 (BCrypt). Cambiar en producción.
INSERT INTO usuarios (email, password_hash, nombre, apellido, rol_id, activo) VALUES
    ('admin@techstore.py', '$2a$10$W.OwXtPsA.6V/wJLPuGBQeHXahfsM4yzQjZdGexZdzJXwrnR8IaEy', 'Admin', 'Sistema', 1, TRUE);

INSERT INTO marcas (nombre, descripcion) VALUES
    ('Intel', 'Procesadores'),
    ('NVIDIA', 'Tarjetas gráficas'),
    ('Samsung', 'Almacenamiento'),
    ('Corsair', 'Fuentes y accesorios'),
    ('Kingston', 'Memoria RAM');

INSERT INTO productos (nombre, descripcion, precio, iva, stock_real, marca_id) VALUES
    ('Intel Core i9-13900K', 'Procesador de 24 núcleos', 2500000.00, 10, 5, 1),
    ('NVIDIA RTX 4090', 'Tarjeta gráfica flagship', 3500000.00, 10, 2, 2),
    ('Samsung 1TB SSD', 'Almacenamiento ultra-rápido', 600000.00, 10, 15, 3),
    ('Kingston 64GB RAM', 'Memoria DDR5', 800000.00, 10, 10, 5);
