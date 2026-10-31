import 'package:flutter_test/flutter_test.dart';
import 'package:techstore_mobile/features/orders/domain/order_tracking.dart';

/// El recorrido del pedido junta tres estados del servidor en uno solo. Si la
/// cuenta se corre un paso, el cliente lee «En camino» con el pago todavía en
/// revisión: estas pruebas fijan cada combinación que importa.
void main() {
  List<String> etiquetas(OrderTracking t) => [for (final s in t.steps) s.label];
  String? actual(OrderTracking t) => t.current?.label;

  group('con pago por transferencia', () {
    test('a domicilio recorre pago, verificación, preparación y entrega', () {
      final t = orderTracking(
        estado: 'P',
        estadoPago: 'EN_VERIFICACION',
        metodoEntrega: 'ENVIO_DOMICILIO',
      );
      expect(etiquetas(t), [
        'Pago pendiente',
        'Verificación',
        'Pago aprobado',
        'En preparación',
        'Listo para despachar',
        'En camino',
        'Entregado',
      ]);
      expect(actual(t), 'Verificación');
      expect(t.summary.label, 'Verificando el pago');
    });

    test('sin comprobante, el paso actual es el pago', () {
      final t = orderTracking(
        estado: 'P',
        estadoPago: 'PENDIENTE_COMPROBANTE',
        metodoEntrega: 'ENVIO_DOMICILIO',
      );
      expect(actual(t), 'Pago pendiente');
      expect(t.summary.label, 'Falta el comprobante');
    });

    test('un rechazo marca la verificación con el motivo', () {
      final t = orderTracking(
        estado: 'P',
        estadoPago: 'RECHAZADO',
        metodoEntrega: 'ENVIO_DOMICILIO',
        motivoRechazo: 'El monto no coincide',
      );
      final verificacion = t.steps[1];
      expect(verificacion.state, EstadoDePaso.failed);
      expect(verificacion.detail, 'El monto no coincide');
      expect(t.summary.label, 'Pago rechazado');
    });

    test('aprobado, avanza con el envío', () {
      final enPreparacion = orderTracking(
        estado: 'G',
        estadoPago: 'APROBADO',
        metodoEntrega: 'ENVIO_DOMICILIO',
        estadoEnvio: 'PREPARANDO',
      );
      expect(actual(enPreparacion), 'En preparación');
      expect(
        enPreparacion.steps.take(3).every((s) => s.state == EstadoDePaso.done),
        isTrue,
      );

      final enCamino = orderTracking(
        estado: 'G',
        estadoPago: 'APROBADO',
        metodoEntrega: 'ENVIO_DOMICILIO',
        estadoEnvio: 'EN_CAMINO',
      );
      expect(actual(enCamino), 'En camino');
      expect(enCamino.summary.label, 'En camino');
    });

    test('entregado deja todos los pasos hechos', () {
      final t = orderTracking(
        estado: 'N',
        estadoPago: 'APROBADO',
        metodoEntrega: 'ENVIO_DOMICILIO',
      );
      expect(t.steps.every((s) => s.state == EstadoDePaso.done), isTrue);
      expect(t.summary.label, 'Entregado');
    });

    test('para retirar no tiene «En camino» y termina en «Retirado»', () {
      final t = orderTracking(
        estado: 'G',
        estadoPago: 'APROBADO',
        metodoEntrega: 'RETIRO_TIENDA',
        estadoEnvio: 'LISTO_PARA_ENVIO',
      );
      expect(etiquetas(t), isNot(contains('En camino')));
      expect(etiquetas(t).last, 'Retirado');
      expect(actual(t), 'Listo para retirar');
    });
  });

  group('con pago en el local', () {
    test('recibido, confirmado, preparación, listo y retirado', () {
      final t = orderTracking(
        estado: 'P',
        estadoPago: 'NO_REQUERIDO',
        metodoEntrega: 'RETIRO_TIENDA',
      );
      expect(etiquetas(t), [
        'Recibido',
        'Confirmado',
        'En preparación',
        'Listo para retirar',
        'Retirado',
      ]);
      expect(actual(t), 'Recibido');
    });

    test('listo para retirar se anuncia en el resumen', () {
      final t = orderTracking(
        estado: 'C',
        estadoPago: 'NO_REQUERIDO',
        metodoEntrega: 'RETIRO_TIENDA',
        estadoEnvio: 'LISTO_PARA_ENVIO',
      );
      expect(t.summary.label, 'Listo para retirar');
    });
  });

  test('un pedido cancelado no muestra pasos', () {
    final t = orderTracking(
      estado: 'X',
      estadoPago: 'EN_VERIFICACION',
      metodoEntrega: 'ENVIO_DOMICILIO',
    );
    expect(t.cancelled, isTrue);
    expect(t.steps, isEmpty);
    expect(t.summary.label, 'Cancelado');
  });
}
