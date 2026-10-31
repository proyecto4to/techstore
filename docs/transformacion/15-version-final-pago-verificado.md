# 15 — Versión final 1.0.24: cobro por transferencia verificado

Fecha: 2026-10-31 · Rama: `version-final` (sale de `rediseno-monokai`).

## La regla de negocio que cambió

**Todo pedido a domicilio se paga antes, por transferencia, y la tienda
verifica el comprobante antes de prepararlo.** El efectivo al repartidor ya no
se ofrece para pedidos nuevos; los que ya existían con ese medio siguen su
curso como antes.

El servidor es quien hace cumplir la regla, no la pantalla:

| Qué | Dónde se valida |
|---|---|
| Domicilio solo con transferencia | `PedidoService.validarMedioPago` |
| Sin datos bancarios cargados no hay transferencia | `PedidoService.checkout` |
| El total transferido tiene que coincidir con el calculado | `CheckoutRequest.totalEsperado` |
| No se confirma un pedido con el pago sin verificar | `PedidoService.confirmar` |
| No se cobra a mano ni lo cobra el repartidor | `PagoService.registrarPago` |
| No se prepara, despacha ni entrega sin confirmar y cobrar | `EnvioService.actualizar` |
| El cliente no cancela solo con el pago en revisión | `PedidoService.cancelar` |
| Comprobante: formato por firma de bytes, 5 MB, 3 en revisión, 12 en total | `PagoComprobanteService` |

## Cómo queda el circuito

1. El cliente elige domicilio (o retiro) y la forma de pago.
2. Con transferencia ve el monto exacto, los datos del banco con botón de
   copiar y el QR del banco si la tienda lo cargó.
3. Transfiere y adjunta el comprobante (captura, PDF o foto). Se sube en el
   momento, con progreso, vista previa y opción de reemplazar o quitar. Sin
   comprobante no se puede continuar.
4. Confirma: el pedido nace **Pendiente** con el pago **En verificación**.
   Cada reintento usa la misma clave de idempotencia, así que un corte de red
   no crea pedidos duplicados.
5. Quien cobra (Admin o Cajero) lo ve en **Verificar pagos** con aviso push.
6. **Aprobar** confirma el pedido (descuenta stock), registra el cobro por
   transferencia, emite la factura y pone el envío en preparación, en una sola
   transacción. **Rechazar** exige un motivo; el pedido sigue reservado y el
   cliente recibe el aviso y puede mandar otro comprobante.
7. El cliente sigue el recorrido: Pago pendiente → Verificación → Pago
   aprobado → En preparación → Listo → En camino → Entregado (o Listo para
   retirar → Retirado).

Retiro en el local con pago en el local sigue como antes, y ahora se completa:
«listo para retirar» avisa al cliente, y marcar el envío como entregado (con el
pedido cobrado) cierra el pedido.

## Base de datos (V58)

- `pedidos.estado_pago` (NO_REQUERIDO, PENDIENTE_COMPROBANTE, EN_VERIFICACION,
  APROBADO, RECHAZADO), motivo del rechazo, quién y cuándo verificó.
- `pago_comprobantes.estado`, motivo, revisor; `pedido_id` admite nulo para el
  comprobante subido antes del checkout (los sueltos se borran a las 24 h).
- `pedido_eventos`: la línea de tiempo del pedido, solo se agregan filas.
- Los pedidos por transferencia que ya existían quedan con el estado de pago
  que realmente tienen.

## Datos que tiene que cargar la tienda

**Administración → Cobro por transferencia** (app) — banco, titular, RUC o CI,
tipo y número de cuenta o alias, una aclaración y, si se quiere, la imagen del
QR que genera la app del banco. **Hasta que se carguen, no hay transferencia ni
envío a domicilio**: la app lo dice en el checkout. No se inventa ningún dato
ni se genera un QR propio, porque no lo leería ninguna app bancaria.

## El resto de los cambios

- **Notificaciones**: campana arriba a la izquierda en los tres inicios con el
  contador; lista agrupada por día, ícono por tipo, hora, leídas y pendientes
  distintas, marcar una o todas. Avisos nuevos: comprobante recibido, pago para
  verificar (al personal), pago rechazado con motivo, listo para retirar.
- **Mantener sesión iniciada**: casilla en el ingreso, apagada de entrada. Sin
  ella el token vive solo en memoria y la sesión se corta al salir de la app;
  con ella, el token de renovación se guarda cifrado y sobrevive a cerrar la
  app. Cerrar sesión borra ambos. La contraseña no se guarda nunca.
- **Atención al Cliente** abre la conversación directamente; la consulta desde
  un pedido queda atada a ese pedido; los mensajes sin conexión muestran un
  aviso con «Reintentar».
- **Web**: el carrito aplica la misma regla y pide el comprobante; hay una
  pestaña «Verificar pagos» en la administración.
