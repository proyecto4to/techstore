-- Row Level Security: el aislamiento entre empresas, también en la base.
--
-- Hasta acá el aislamiento lo garantiza Hibernate (@TenantId), y funciona. Pero
-- depende de pasar siempre por Hibernate: una consulta nativa, un script de
-- mantenimiento o un bug futuro pueden esquivarlo. RLS lo mueve al motor, donde
-- ya no hay forma de olvidarse.
--
-- IMPORTANTE — esto NO tiene efecto por sí solo.
-- Las políticas no se aplican a superusuarios ni a roles con BYPASSRLS, ni al
-- dueño de la tabla salvo que se fuerce. Hoy la aplicación conecta como
-- techstore_user, que es superusuario: para el motor puede verlo todo.
-- Para que esto sirva hay que correr configurar-rol-app.ps1, que crea un rol
-- sin privilegios de administrador y apunta la aplicación a ese rol.
-- Mientras eso no se haga, el sistema sigue funcionando igual y protegido por
-- Hibernate; RLS queda armado esperando.

-- Función única, para no repetir la expresión en veinte políticas.
CREATE OR REPLACE FUNCTION tenant_actual() RETURNS BIGINT AS $$
    -- El segundo argumento en true hace que devuelva NULL en vez de error si la
    -- variable no está seteada. Eso importa: con NULL, la comparación
    -- "tenant_id = NULL" da NULL y la fila NO se ve. Falla cerrado, que es como
    -- tiene que fallar algo que separa los datos de dos clientes.
    SELECT NULLIF(current_setting('app.tenant_id', true), '')::BIGINT;
$$ LANGUAGE SQL STABLE;

COMMENT ON FUNCTION tenant_actual() IS
    'Empresa de la conexión actual. La fija la aplicación con SET app.tenant_id al tomar la conexión.';

DO $$
DECLARE
    t TEXT;
    -- Las mismas tablas que llevan tenant_id. roles e intento_login quedan
    -- afuera por los motivos explicados en V23.
    tablas TEXT[] := ARRAY[
        'usuarios', 'clientes', 'proveedores', 'marcas', 'modelos', 'productos',
        'pedidos', 'pedido_detalles', 'facturas', 'pagos',
        'notas_credito', 'nota_credito_detalles',
        'movimiento_caja', 'cierres_caja', 'aperturas_caja',
        'correos', 'audit_log', 'autonumerador', 'configuracion'
    ];
BEGIN
    FOREACH t IN ARRAY tablas LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
        -- FORCE: sin esto, el dueño de la tabla se saltea sus propias políticas.
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', t);

        EXECUTE format('DROP POLICY IF EXISTS aislamiento_tenant ON %I', t);
        EXECUTE format($f$
            CREATE POLICY aislamiento_tenant ON %I
                USING (tenant_id = tenant_actual())
                WITH CHECK (tenant_id = tenant_actual())
        $f$, t);

        -- Las migraciones y el mantenimiento corren sin app.tenant_id seteado y
        -- necesitan ver todo. Esta política se lo permite SOLO al dueño de la
        -- tabla, que es quien ejecuta Flyway; el rol de la aplicación no la
        -- cumple nunca.
        EXECUTE format('DROP POLICY IF EXISTS mantenimiento ON %I', t);
        EXECUTE format($f$
            CREATE POLICY mantenimiento ON %I
                TO %I
                USING (tenant_actual() IS NULL)
                WITH CHECK (tenant_actual() IS NULL)
        $f$, t, current_user);
    END LOOP;
END $$;
