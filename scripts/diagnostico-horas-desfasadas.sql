-- Cuánto dato quedó con la hora corrida, y en qué tablas.
--
-- NO MODIFICA NADA. Es solo para ver el tamaño del problema antes de decidir
-- qué hacer con los registros viejos.
--
-- ---------------------------------------------------------------------------
-- El problema
-- ---------------------------------------------------------------------------
-- El JDK 22 trae la base de zonas 2024a, que todavía cree que Paraguay cambia
-- de hora. Paraguay está en -03:00 permanente desde 2024, así que:
--
--   * de octubre a marzo el JDK dice -03:00  -> la hora quedó BIEN
--   * de abril a septiembre  dice -04:00     -> la hora quedó UNA HORA ATRÁS
--
-- Por eso el desfase es estacional y no constante: sumarle una hora a todo
-- rompería los registros que sí están bien. Cualquier corrección tiene que
-- acotarse a las ventanas de abril a septiembre.
--
-- Desde el arreglo en `iniciar-techstore.ps1` (-Duser.timezone=Etc/GMT+3) lo
-- que se registra de acá en adelante queda con la hora correcta.
--
-- ---------------------------------------------------------------------------
-- Cómo usarlo
-- ---------------------------------------------------------------------------
--   tools\pgsql\bin\psql -U techstore_user -d techstore_db -f scripts\diagnostico-horas-desfasadas.sql
--
-- ---------------------------------------------------------------------------

\echo '== Registros con la hora una hora atrasada, por tabla =='

WITH ventanas AS (
    -- Los tramos en los que el JDK viejo se desviaba de la hora legal.
    SELECT anio,
           make_timestamp(anio, 4, 1, 0, 0, 0)  AS desde,
           make_timestamp(anio, 10, 1, 0, 0, 0) AS hasta
    FROM generate_series(2024, EXTRACT(YEAR FROM CURRENT_DATE)::int) AS anio
),
afectados AS (
    SELECT 'pedidos'         AS tabla, p.fecha        AS momento FROM pedidos p
    UNION ALL SELECT 'facturas',        f.fecha_emision       FROM facturas f
    UNION ALL SELECT 'pagos',           pg.fecha              FROM pagos pg
    UNION ALL SELECT 'movimiento_caja', m.fecha               FROM movimiento_caja m
    UNION ALL SELECT 'aperturas_caja',  a.fecha_apertura      FROM aperturas_caja a
    UNION ALL SELECT 'cierres_caja',    c.fecha_cierre        FROM cierres_caja c
)
SELECT a.tabla,
       COUNT(*) FILTER (WHERE EXISTS (
           SELECT 1 FROM ventanas v WHERE a.momento >= v.desde AND a.momento < v.hasta
       )) AS con_hora_atrasada,
       COUNT(*) AS total,
       MIN(a.momento) AS mas_antiguo,
       MAX(a.momento) AS mas_reciente
FROM afectados a
GROUP BY a.tabla
ORDER BY con_hora_atrasada DESC;

\echo ''
\echo '== Antes de tocar nada =='
\echo 'Las facturas llevan timbrado y numeración fiscal: cambiarles la hora'
\echo 'después de emitidas es una decisión contable, no técnica. Consultalo'
\echo 'antes de correr cualquier UPDATE sobre esa tabla.'
\echo ''
\echo 'Para las tablas operativas (movimiento_caja, aperturas_caja,'
\echo 'cierres_caja), si decidís corregirlas, la forma es sumar una hora SOLO'
\echo 'dentro de las ventanas de abril a septiembre, con respaldo hecho y'
\echo 'dentro de una transacción que puedas revertir.'
