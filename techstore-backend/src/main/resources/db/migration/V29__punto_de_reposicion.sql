-- Punto de reposición calculado.
--
-- Hasta acá el aviso de "reponer" salía cuando el stock caía por debajo de un
-- número cargado a mano (stock_minimo), o de 5 si no había ninguno. Ese número
-- no sabe nada del producto: el mismo 5 para algo que se vende de a 20 por
-- semana y llega en dos días, que para algo que se vende de a 1 por mes y
-- tarda un mes en llegar.
--
-- El punto de reposición de verdad es cuánto se vende mientras se espera al
-- proveedor:
--
--   punto = demanda diaria × días de entrega + stock de seguridad
--
-- Para eso hacen falta dos cosas que el sistema no guardaba.
--
-- 1. CUANDO LLEGO LA MERCADERIA
--
-- Al recibir una compra solo se cambiaba el estado, sin registrar cuándo. Sin
-- esa fecha no se puede medir cuánto tarda un proveedor: se sabe cuándo se le
-- pidió y nada más.

ALTER TABLE pedidos ADD COLUMN fecha_recepcion TIMESTAMP;

COMMENT ON COLUMN pedidos.fecha_recepcion IS
    'Cuándo se recibió la mercadería de una compra. Nulo en ventas y en compras pendientes. '
    'La diferencia contra fecha es lo que tardó el proveedor.';

-- 2. CUANTOS DIAS DE COBERTURA SE QUIEREN
--
-- El stock de seguridad es una decisión del negocio, no un cálculo: cuántos
-- días de venta se quieren tener de colchón por si el proveedor se atrasa o la
-- demanda salta. Eso vive en application.properties (reposicion.*), como el
-- resto de los parámetros que no cambian por empresa.
--
-- La demanda diaria y los días de entrega no se guardan: se calculan del
-- historial cada vez, así siempre reflejan lo último que pasó.
