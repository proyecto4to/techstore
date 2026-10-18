/// Qué parte de la aplicación toca refrescar según el evento que llegó.
///
/// El servidor nombra los agregados en inglés y en mayúsculas —`PRODUCT`,
/// `ORDER`, `DELIVERY_ADDRESS`—, tal como los declara el disparador de la base
/// que los encola. Antes esto se resolvía con `aggregate.contains('producto')`,
/// que no coincide nunca: `'PRODUCT'.toLowerCase()` es `'product'`, no
/// `'producto'`. La aplicación tenía toda la cañería de tiempo real andando
/// —disparadores, outbox, WebSocket, reconexión por cursor— y no refrescaba
/// nada, porque el último paso comparaba castellano contra inglés.
///
/// Por eso la correspondencia vive acá, con los códigos escritos tal cual, y
/// no repartida en comparaciones de texto: un fallo así no rompe nada, no
/// aparece en la consola y no lo agarra ningún test que mire pantallas. Lo
/// único que lo previene es que la lista sea explícita y esté comprobada.
library;

/// Todos los agregados que la aplicación puede recibir hoy.
///
/// Casi todos salen de los disparadores declarados en la migración del outbox
/// y llegan por el canal de la empresa. Los dos últimos —[agregadoNotificacion]
/// y [agregadoMensaje]— no: esos los arma la aplicación cuando llega algo por
/// la cola personal, que es de una sola persona y no pasa por el outbox.
///
/// Si en el servidor se agrega una tabla, va también acá: hay un caso que falla
/// si alguno queda sin clasificar, para que no vuelva a pasar que un evento
/// llegue y se ignore en silencio.
const agregadosConocidos = <String>{
  'USER',
  'CLIENT',
  'SUPPLIER',
  'BRAND',
  'MODEL',
  'PRODUCT',
  'ORDER',
  'ORDER_ITEM',
  'PAYMENT',
  'INVOICE',
  'CREDIT_NOTE',
  'CASH_MOVEMENT',
  'CASH_OPENING',
  'CASH_CLOSING',
  'CONFIGURATION',
  'DELIVERY_ADDRESS',
  'SHIPMENT',
  'SHIPPING_RATE',
  'SUPPLIER_OFFER',
  'PURCHASE_DOCUMENT',
  'ISSUING_POINT',
  'PROMOTION',
  agregadoNotificacion,
  agregadoMensaje,
};

/// Un aviso para la campana, llegado por la cola personal.
const agregadoNotificacion = 'NOTIFICATION';

/// Un mensaje del chat —nuevo o marcado como leído—, por la cola personal.
///
/// No hay un agregado aparte para la conversación: cuando alguien abre una,
/// manda un mensaje, y ese mensaje ya avisa a quien corresponda.
const agregadoMensaje = 'MESSAGE';

/// Lo que cambia el catálogo que ve quien compra: precio, stock, disponibilidad.
///
/// `PRODUCT` es el que importa para el stock en vivo: el disparador se activa
/// con cada `UPDATE` de la fila, y vender descuenta ahí mismo.
///
/// `PROMOTION` entra acá porque encender una promoción cambia el precio que se
/// publica sin tocar la fila del producto: sin esto, quien tuviera el catálogo
/// abierto seguiría viendo el precio viejo hasta recargarlo a mano.
const agregadosDeCatalogo = <String>{'PRODUCT', 'BRAND', 'MODEL', 'PROMOTION'};

/// El recorrido de una compra, desde que se arma hasta que se entrega.
const agregadosDePedido = <String>{
  'ORDER',
  'ORDER_ITEM',
  'PAYMENT',
  'SHIPMENT',
};

/// Documentos emitidos.
const agregadosDeFacturacion = <String>{'INVOICE', 'PAYMENT', 'CREDIT_NOTE'};

/// Las direcciones de entrega de quien compra.
const agregadosDeDirecciones = <String>{'DELIVERY_ADDRESS'};

/// Lo que mira el mostrador: caja abierta, movimientos y cierres.
const agregadosDeCaja = <String>{
  'CASH_MOVEMENT',
  'CASH_OPENING',
  'CASH_CLOSING',
};

/// Agregados que no tienen una pantalla propia a refrescar.
///
/// Igual llegan y se anotan acá a propósito: sin esta lista no se distingue
/// «no hace falta hacer nada» de «se nos olvidó clasificarlo».
const agregadosSinPantallaPropia = <String>{
  'USER',
  'CLIENT',
  'SUPPLIER',
  'CONFIGURATION',
  'SHIPPING_RATE',
  'SUPPLIER_OFFER',
  'PURCHASE_DOCUMENT',
  'ISSUING_POINT',
};

/// Avisos dirigidos a la persona: la campana y su contador.
const agregadosDeNotificaciones = <String>{agregadoNotificacion};

/// Mensajería: un mensaje nuevo o uno que la otra parte acaba de leer.
const agregadosDeConversaciones = <String>{agregadoMensaje};
