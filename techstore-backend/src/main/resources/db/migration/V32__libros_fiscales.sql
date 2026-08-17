-- Los datos que faltaban para poder emitir los libros de IVA y el archivo del
-- Registro de Comprobantes de la DNIT (RG 90/2021).
--
-- QUÉ PIDE LA DNIT
--
-- El Registro de Comprobantes reemplazó a Hechauka: se sube a Marangatu un
-- archivo CSV o TXT, una fila por comprobante, con el tipo de comprobante, el
-- timbrado, el número, la identificación de la contraparte, el desglose por
-- tasa de IVA y la condición de la operación.
--
-- El sistema tenía tres de esos datos y le faltaban cuatro.
--
-- EL TIMBRADO SE CONGELA EN LA FACTURA
--
-- Hasta acá el timbrado vivía solo en la configuración de la empresa, o sea que
-- era uno solo y siempre el vigente. Un timbrado vence y se reemplaza; si el
-- libro de enero se imprime en diciembre, tiene que salir con el timbrado que
-- tenía la factura cuando se emitió, no con el de hoy. Por eso pasa a ser una
-- columna del documento, como ya lo son el receptor y la moneda.
--
-- Queda nullable a propósito: las facturas emitidas antes de esta migración se
-- rellenan con el timbrado configurado —que es el único que hubo— pero si la
-- empresa nunca cargó uno, un nulo dice la verdad ("no sabemos con qué timbrado
-- salió") mejor que un cero inventado. La exportación las marca como pendientes
-- en vez de mandarlas mal.
--
-- LA CONDICIÓN DE VENTA DEJA DE ESTAR EN DURO
--
-- El campo 13 del archivo distingue contado de crédito. El sistema mandaba
-- "Contado" fijo en el XML del SIFEN y en la columna `tipo` de la factura.
-- Ahora es un dato del documento. Todo lo emitido hasta hoy fue al contado, así
-- que el relleno es exacto, no una suposición.
--
-- `tipo` se va: era un texto libre que guardaba 'CONTADO' y que tres lugares
-- comparaban con literales distintos —uno preguntaba si era 'CONTADO' y otro si
-- no era 'CREDITO'—. Con `condicion_venta` diciendo lo mismo, dejar los dos
-- garantiza que algún día se contradigan.
--
-- EL COMPROBANTE DEL PROVEEDOR NO EXISTÍA
--
-- Sin el número, el timbrado y la fecha de la factura que emitió el proveedor
-- no hay libro de compras posible: una compra registrada en el sistema es una
-- orden de pedido, no un comprobante fiscal. Va en tabla aparte y no en
-- `pedidos` porque es un documento ajeno, con su propia identidad y su propia
-- unicidad, y porque puede no existir todavía: la compra se carga cuando se
-- pide y el comprobante llega con la mercadería.

-- Contado o crédito, para ventas y para compras (Tabla 2 de la especificación).
CREATE TYPE condicion_operacion AS ENUM ('Contado', 'Credito');

-- --------------------------------------------------------------------------
-- Facturas emitidas
-- --------------------------------------------------------------------------

ALTER TABLE facturas
    ADD COLUMN timbrado                VARCHAR(8),
    ADD COLUMN condicion_venta         condicion_operacion NOT NULL DEFAULT 'Contado',
    ADD COLUMN receptor_tipo_documento SMALLINT;

COMMENT ON COLUMN facturas.timbrado IS
    'Timbrado con el que se emitió, congelado. Nulo = se emitió antes de que el sistema lo registrara.';
COMMENT ON COLUMN facturas.condicion_venta IS
    'Campo 13 del Registro de Comprobantes de la DNIT. Hoy el sistema solo vende al contado.';
COMMENT ON COLUMN facturas.receptor_tipo_documento IS
    'Tabla 3 de la DNIT: 11 RUC, 12 cédula, 13 pasaporte, 14 cédula extranjero, 15 sin nombre, 16 diplomático, 17 identificación tributaria.';

-- El único timbrado que hubo es el configurado: las facturas ya emitidas
-- salieron con ese.
UPDATE facturas f
SET timbrado = c.valor
FROM configuracion c
WHERE c.tenant_id = f.tenant_id
  AND c.clave = 'empresa.timbrado'
  AND c.valor IS NOT NULL
  AND c.valor <> ''
  AND f.timbrado IS NULL;

-- Con RUC se facturó a un contribuyente; sin RUC fue a consumidor final, que
-- para la DNIT es el tipo 15 (SIN NOMBRE).
UPDATE facturas
SET receptor_tipo_documento = CASE WHEN receptor_ruc IS NULL THEN 15 ELSE 11 END
WHERE receptor_tipo_documento IS NULL;

ALTER TABLE facturas
    ALTER COLUMN receptor_tipo_documento SET NOT NULL,
    ALTER COLUMN receptor_tipo_documento SET DEFAULT 11;

-- Lo que decía `tipo` pasa a decirlo `condicion_venta`, y la columna vieja se
-- va. El DEFAULT ya dejó todo en 'Contado'; esto rescata cualquier fila que
-- hubiera quedado marcada como crédito.
UPDATE facturas SET condicion_venta = 'Credito' WHERE upper(tipo) = 'CREDITO';

ALTER TABLE facturas DROP COLUMN tipo;

-- --------------------------------------------------------------------------
-- Comprobante recibido del proveedor
-- --------------------------------------------------------------------------

CREATE TABLE compra_comprobantes (
    id                    BIGSERIAL PRIMARY KEY,
    tenant_id             BIGINT      NOT NULL DEFAULT 1 REFERENCES tenants(id),
    -- La compra a la que corresponde. Uno por compra: si el proveedor mandó dos
    -- facturas hay que cargar dos compras, porque cada comprobante se declara
    -- por separado.
    pedido_id             BIGINT      NOT NULL REFERENCES pedidos(id),

    -- Tabla 4 de la DNIT. 109 = FACTURA, que es el caso normal; 101 autofactura,
    -- 107 despacho de importación, 110 nota de crédito, 112 ticket.
    tipo_comprobante      SMALLINT    NOT NULL DEFAULT 109,
    -- Nulos solo donde la especificación los permite: el despacho de importación
    -- no lleva timbrado, y el ticket de máquina registradora no lleva número.
    timbrado              VARCHAR(8),
    numero                VARCHAR(20),
    fecha_emision         DATE        NOT NULL,
    condicion             condicion_operacion NOT NULL DEFAULT 'Contado',

    -- El proveedor se congela igual que el receptor en la factura: si mañana
    -- corrige su razón social, el libro de un período ya cerrado no cambia.
    tipo_identificacion   SMALLINT    NOT NULL DEFAULT 11,
    proveedor_ruc         VARCHAR(20),
    proveedor_razon_social VARCHAR(255),

    moneda                moneda      NOT NULL DEFAULT 'PYG',
    -- Con el IVA incluido, como el resto del sistema: la DNIT los pide así.
    gravado_10            NUMERIC(15,2) NOT NULL DEFAULT 0,
    gravado_5             NUMERIC(15,2) NOT NULL DEFAULT 0,
    exento                NUMERIC(15,2) NOT NULL DEFAULT 0,
    total                 NUMERIC(15,2) NOT NULL,

    -- Nota de crédito o de débito del proveedor: a qué comprobante corrige.
    asociado_numero       VARCHAR(20),
    asociado_timbrado     VARCHAR(8),

    created_at            TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT comprobante_por_compra UNIQUE (tenant_id, pedido_id),
    -- Un timbrado pertenece a un solo contribuyente, así que el par
    -- timbrado+número identifica al comprobante en todo el país. Cargar dos
    -- veces la misma factura del proveedor duplicaría el crédito fiscal.
    CONSTRAINT comprobante_proveedor_unico UNIQUE (tenant_id, timbrado, numero),

    CONSTRAINT comprobante_importes_positivos CHECK (
        gravado_10 >= 0 AND gravado_5 >= 0 AND exento >= 0 AND total > 0
    ),
    -- El total tiene que ser la suma del desglose. La especificación exceptúa a
    -- los comprobantes que no dan crédito fiscal (autofactura, boleta resimple,
    -- lotería, ticket de máquina registradora): de esos se informa solo el total
    -- y el desglose va en cero.
    CONSTRAINT comprobante_desglose_cuadra CHECK (
        CASE WHEN tipo_comprobante IN (101, 104, 105, 112)
             THEN gravado_10 = 0 AND gravado_5 = 0 AND exento = 0
             ELSE total = gravado_10 + gravado_5 + exento
        END
    )
);

CREATE INDEX idx_compra_comprobante_fecha ON compra_comprobantes (tenant_id, fecha_emision);

COMMENT ON TABLE compra_comprobantes IS
    'El comprobante fiscal que emitió el proveedor. Es lo que se asienta en el libro de compras y lo que da el crédito fiscal; la compra en sí es un pedido interno.';
COMMENT ON COLUMN compra_comprobantes.tipo_comprobante IS
    'Tabla 4 de la DNIT: 109 factura, 101 autofactura, 107 despacho de importación, 110 nota de crédito, 111 nota de débito, 112 ticket.';
COMMENT ON COLUMN compra_comprobantes.gravado_10 IS
    'Monto gravado al 10% con el IVA incluido, como lo pide el Registro de Comprobantes.';

-- Mismo aislamiento por empresa que el resto del dominio (patrón de V24).
ALTER TABLE compra_comprobantes ENABLE ROW LEVEL SECURITY;
ALTER TABLE compra_comprobantes FORCE ROW LEVEL SECURITY;

CREATE POLICY aislamiento_tenant ON compra_comprobantes
    USING (tenant_id = tenant_actual())
    WITH CHECK (tenant_id = tenant_actual());

DO $$
BEGIN
    EXECUTE format($f$
        CREATE POLICY mantenimiento ON compra_comprobantes
            TO %I
            USING (tenant_actual() IS NULL)
            WITH CHECK (tenant_actual() IS NULL)
    $f$, current_user);
END $$;
