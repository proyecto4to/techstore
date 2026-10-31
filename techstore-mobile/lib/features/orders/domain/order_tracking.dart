import 'package:flutter/material.dart';

import '../../../core/format/status_labels.dart';
import '../../payments/domain/payment_models.dart';

/// Cómo va un paso del recorrido del pedido.
enum EstadoDePaso { done, current, failed, pending }

final class TrackingStep {
  const TrackingStep(this.label, this.icon, this.state, {this.detail});

  final String label;
  final IconData icon;
  final EstadoDePaso state;

  /// Lo que se le explica a la persona en el paso en curso.
  final String? detail;
}

/// El recorrido de un pedido, de la compra a la entrega.
///
/// Junta tres estados que el servidor guarda por separado —el del pedido, el
/// de la verificación del pago y el del envío— porque a la persona le
/// interesa una sola cosa: en qué paso está y qué falta. Mostrar los tres
/// sueltos obligaba a deducir que «Pendiente» + «En verificación» +
/// «Pendiente de preparación» quería decir «estamos mirando tu transferencia».
final class OrderTracking {
  const OrderTracking({
    required this.steps,
    required this.summary,
    this.cancelled = false,
  });

  final List<TrackingStep> steps;

  /// El estado en una frase, para listas y encabezados.
  final StatusLabel summary;
  final bool cancelled;

  TrackingStep? get current => steps
      .where(
        (s) =>
            s.state == EstadoDePaso.current || s.state == EstadoDePaso.failed,
      )
      .firstOrNull;
}

/// Nivel del envío: 0 sin empezar, 1 preparando, 2 listo, 3 en camino, 4 entregado.
int _nivelDeEnvio(String? envio) => switch (envio?.trim().toUpperCase()) {
  'PREPARANDO' => 1,
  'LISTO_PARA_ENVIO' => 2,
  'DESPACHADO' || 'EN_CAMINO' || 'INTENTO_ENTREGA' => 3,
  'ENTREGADO' => 4,
  _ => 0,
};

OrderTracking orderTracking({
  required String? estado,
  required String? estadoPago,
  required String? metodoEntrega,
  String? estadoEnvio,
  String? motivoRechazo,
}) {
  final pedido = estado?.trim().toUpperCase() ?? '';
  final pago = PaymentState.parse(estadoPago);
  final aDomicilio = metodoEntrega?.trim().toUpperCase() == 'ENVIO_DOMICILIO';
  var envio = _nivelDeEnvio(estadoEnvio);
  // Un pedido entregado cuenta como entregado aunque el envío no se haya
  // actualizado: hay pedidos viejos que se cerraron sin pasar por el envío.
  if (pedido == 'N') envio = 4;

  if (pedido == 'X') {
    return const OrderTracking(
      steps: [],
      cancelled: true,
      summary: StatusLabel(
        'Cancelado',
        detail: 'Este pedido ya no se va a procesar.',
        icon: Icons.cancel_outlined,
        tone: StatusTone.danger,
      ),
    );
  }
  if (pedido == 'R') {
    return OrderTracking(steps: const [], summary: orderStatusLabel('R'));
  }

  final listo = aDomicilio ? 'Listo para despachar' : 'Listo para retirar';
  final cierre = aDomicilio ? 'Entregado' : 'Retirado';

  if (pago != PaymentState.notRequired) {
    // Con pago previo: primero el pago, después la preparación.
    final int actual;
    final bool fallido = pago == PaymentState.rejected;
    switch (pago) {
      case PaymentState.waitingReceipt:
        actual = 0;
      case PaymentState.inVerification:
      case PaymentState.rejected:
        actual = 1;
      case PaymentState.approved:
      case PaymentState.notRequired:
        // Aprobado: desde «Pago aprobado» avanza con el envío.
        actual = switch (envio) {
          0 => 2,
          1 => 3,
          2 => 4,
          3 => aDomicilio ? 5 : 4,
          _ => aDomicilio ? 6 : 5,
        };
    }
    final pasos = <(String, IconData, String?)>[
      (
        'Pago pendiente',
        Icons.account_balance_outlined,
        'Adjuntá el comprobante de la transferencia.',
      ),
      (
        'Verificación',
        Icons.fact_check_outlined,
        fallido
            ? (motivoRechazo ?? 'No pudimos validar el comprobante.')
            : 'Estamos confirmando que la transferencia llegó.',
      ),
      (
        'Pago aprobado',
        Icons.verified_outlined,
        'Recibimos tu pago y emitimos la factura.',
      ),
      (
        'En preparación',
        Icons.inventory_2_outlined,
        'Estamos preparando tu pedido.',
      ),
      (
        listo,
        aDomicilio ? Icons.local_shipping_outlined : Icons.storefront_outlined,
        aDomicilio
            ? 'Sale en el próximo reparto.'
            : 'Ya podés pasar a buscarlo.',
      ),
      if (aDomicilio)
        (
          'En camino',
          Icons.directions_car_outlined,
          'Va camino a tu dirección.',
        ),
      (cierre, Icons.check_circle_outline, null),
    ];
    final terminado = envio == 4;
    final steps = [
      for (var i = 0; i < pasos.length; i++)
        TrackingStep(
          pasos[i].$1,
          pasos[i].$2,
          terminado || i < actual
              ? EstadoDePaso.done
              : i == actual
              ? (fallido ? EstadoDePaso.failed : EstadoDePaso.current)
              : EstadoDePaso.pending,
          detail: i == actual ? pasos[i].$3 : null,
        ),
    ];
    return OrderTracking(
      steps: steps,
      summary: _resumen(pago, pedido, envio, aDomicilio),
    );
  }

  // Sin pago previo: retiro con pago en el local, o pedidos anteriores a la regla.
  final int actual = switch (pedido) {
    'P' => 0,
    _ => switch (envio) {
      0 || 1 => 2,
      // A domicilio, «listo para despachar» todavía es parte de prepararlo.
      2 => aDomicilio ? 2 : 3,
      3 => 3,
      _ => 4,
    },
  };
  final pasos = <(String, IconData, String?)>[
    ('Recibido', Icons.receipt_long_outlined, 'Estamos revisando tu pedido.'),
    ('Confirmado', Icons.inventory_outlined, null),
    (
      'En preparación',
      Icons.inventory_2_outlined,
      'Estamos preparando tu pedido.',
    ),
    if (aDomicilio)
      ('En camino', Icons.directions_car_outlined, 'Va camino a tu dirección.')
    else
      (
        listo,
        Icons.storefront_outlined,
        'Pasá a buscarlo y lo pagás en la tienda.',
      ),
    (cierre, Icons.check_circle_outline, null),
  ];
  final terminado = envio == 4;
  return OrderTracking(
    steps: [
      for (var i = 0; i < pasos.length; i++)
        TrackingStep(
          pasos[i].$1,
          pasos[i].$2,
          terminado || i < actual
              ? EstadoDePaso.done
              : i == actual
              ? EstadoDePaso.current
              : EstadoDePaso.pending,
          detail: i == actual ? pasos[i].$3 : null,
        ),
    ],
    summary: switch (pedido) {
      'P' => orderStatusLabel('P'),
      'N' => orderStatusLabel('N'),
      _ when envio >= 2 && !aDomicilio => const StatusLabel(
        'Listo para retirar',
        icon: Icons.storefront_outlined,
        tone: StatusTone.positive,
      ),
      _ when envio == 3 => const StatusLabel(
        'En camino',
        icon: Icons.directions_car_outlined,
        tone: StatusTone.progress,
      ),
      _ => orderStatusLabel(pedido),
    },
  );
}

StatusLabel _resumen(
  PaymentState pago,
  String pedido,
  int envio,
  bool aDomicilio,
) {
  switch (pago) {
    case PaymentState.waitingReceipt:
      return const StatusLabel(
        'Falta el comprobante',
        detail: 'Adjuntá el comprobante de la transferencia para que podamos verificarla.',
        icon: Icons.upload_file_outlined,
        tone: StatusTone.warning,
      );
    case PaymentState.inVerification:
      return const StatusLabel(
        'Verificando el pago',
        detail: 'Estamos confirmando que la transferencia llegó. Te avisamos apenas esté.',
        icon: Icons.fact_check_outlined,
        tone: StatusTone.progress,
      );
    case PaymentState.rejected:
      return const StatusLabel(
        'Pago rechazado',
        detail: 'No pudimos validar el comprobante. Podés cargar uno nuevo.',
        icon: Icons.report_gmailerrorred_outlined,
        tone: StatusTone.danger,
      );
    case PaymentState.approved:
    case PaymentState.notRequired:
      if (pedido == 'N' || envio == 4) {
        return StatusLabel(
          aDomicilio ? 'Entregado' : 'Retirado',
          icon: Icons.check_circle_outline,
          tone: StatusTone.positive,
        );
      }
      if (envio == 3) {
        return const StatusLabel(
          'En camino',
          icon: Icons.directions_car_outlined,
          tone: StatusTone.progress,
        );
      }
      if (envio == 2) {
        return StatusLabel(
          aDomicilio ? 'Listo para despachar' : 'Listo para retirar',
          icon: aDomicilio
              ? Icons.local_shipping_outlined
              : Icons.storefront_outlined,
          tone: StatusTone.positive,
        );
      }
      if (envio == 1) {
        return const StatusLabel(
          'En preparación',
          icon: Icons.inventory_2_outlined,
          tone: StatusTone.progress,
        );
      }
      return const StatusLabel(
        'Pago aprobado',
        detail: 'Recibimos tu pago. Enseguida empezamos a preparar tu pedido.',
        icon: Icons.verified_outlined,
        tone: StatusTone.positive,
      );
  }
}
