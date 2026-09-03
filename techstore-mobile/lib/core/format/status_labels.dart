import 'package:flutter/material.dart';

/// Traducción de los estados internos a lenguaje de cliente.
///
/// El servidor manda códigos (`P`, `G`, `EN_RUTA`) porque son las etiquetas del
/// enum en la base. Mostrarlos tal cual obliga a la persona a aprenderse un
/// diccionario, así que se traducen en un solo lugar: si la traducción viviera
/// en cada pantalla, el mismo pedido se llamaría distinto según dónde se mire.

/// El tono comunica gravedad sin depender solo del color: cada tono trae su
/// propio ícono.
enum StatusTone { neutral, progress, positive, warning, danger }

final class StatusLabel {
  const StatusLabel(this.label, {required this.icon, required this.tone, this.detail});

  /// Lo que se muestra. Nunca es un código interno.
  final String label;

  /// Una línea que explica qué significa y qué sigue, cuando aporta.
  final String? detail;

  final IconData icon;
  final StatusTone tone;

  Color color(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return switch (tone) {
      StatusTone.neutral => scheme.onSurfaceVariant,
      StatusTone.progress => scheme.primary,
      StatusTone.positive => scheme.tertiary,
      StatusTone.warning => scheme.secondary,
      StatusTone.danger => scheme.error,
    };
  }
}

/// Estado del pedido (`EstadoPedido` en el backend).
StatusLabel orderStatusLabel(String? raw) => switch (raw?.trim().toUpperCase()) {
  'P' => const StatusLabel(
    'Pendiente de confirmación',
    detail: 'Estamos revisando tu pedido. Te avisamos apenas lo confirmemos.',
    icon: Icons.hourglass_empty,
    tone: StatusTone.neutral,
  ),
  'C' => const StatusLabel(
    'Confirmado',
    detail: 'Reservamos tus productos. Falta registrar el pago.',
    icon: Icons.inventory_2_outlined,
    tone: StatusTone.progress,
  ),
  'G' => const StatusLabel(
    'Pagado',
    detail: 'Recibimos tu pago y emitimos la factura.',
    icon: Icons.verified_outlined,
    tone: StatusTone.positive,
  ),
  'N' => const StatusLabel(
    'Entregado',
    detail: 'El pedido llegó a destino.',
    icon: Icons.check_circle_outline,
    tone: StatusTone.positive,
  ),
  'R' => const StatusLabel(
    'Devuelto',
    detail: 'Se emitió una nota de crédito sobre la factura.',
    icon: Icons.assignment_return_outlined,
    tone: StatusTone.warning,
  ),
  'X' => const StatusLabel(
    'Cancelado',
    detail: 'Este pedido ya no se va a procesar.',
    icon: Icons.cancel_outlined,
    tone: StatusTone.danger,
  ),
  _ => _fallback(raw),
};

/// Estado del envío (`EstadoEnvio` en el backend).
StatusLabel shipmentStatusLabel(String? raw) =>
    switch (raw?.trim().toUpperCase()) {
      'PENDIENTE' => const StatusLabel(
        'Pendiente de preparación',
        icon: Icons.schedule,
        tone: StatusTone.neutral,
      ),
      'PREPARANDO' => const StatusLabel(
        'Preparando tu pedido',
        icon: Icons.inventory_outlined,
        tone: StatusTone.progress,
      ),
      'LISTO_PARA_ENVIO' => const StatusLabel(
        'Listo para despachar',
        icon: Icons.local_shipping_outlined,
        tone: StatusTone.progress,
      ),
      'DESPACHADO' => const StatusLabel(
        'Despachado',
        icon: Icons.local_shipping_outlined,
        tone: StatusTone.progress,
      ),
      'EN_CAMINO' => const StatusLabel(
        'En camino',
        icon: Icons.directions_car_outlined,
        tone: StatusTone.progress,
      ),
      'INTENTO_ENTREGA' => const StatusLabel(
        'Intentamos entregarlo',
        detail: 'No pudimos entregarlo. Vamos a reintentar.',
        icon: Icons.report_problem_outlined,
        tone: StatusTone.warning,
      ),
      'ENTREGADO' => const StatusLabel(
        'Entregado',
        icon: Icons.check_circle_outline,
        tone: StatusTone.positive,
      ),
      'CANCELADO' => const StatusLabel(
        'Cancelado',
        icon: Icons.cancel_outlined,
        tone: StatusTone.danger,
      ),
      _ => _fallback(raw),
    };

/// Estado de la entrega del repartidor (`EstadoReparto` en el backend).
StatusLabel deliveryStatusLabel(String? raw) =>
    switch (raw?.trim().toUpperCase()) {
      'ASIGNADO' => const StatusLabel(
        'Asignada',
        detail: 'Todavía no la aceptaste.',
        icon: Icons.assignment_outlined,
        tone: StatusTone.neutral,
      ),
      'ACEPTADO' => const StatusLabel(
        'Aceptada',
        detail: 'Lista para salir.',
        icon: Icons.thumb_up_outlined,
        tone: StatusTone.progress,
      ),
      'EN_RUTA' => const StatusLabel(
        'En camino',
        icon: Icons.directions_bike_outlined,
        tone: StatusTone.progress,
      ),
      'EN_DESTINO' => const StatusLabel(
        'En el domicilio',
        detail: 'Llegaste. Falta entregar y cobrar si corresponde.',
        icon: Icons.location_on_outlined,
        tone: StatusTone.progress,
      ),
      'ENTREGADO' => const StatusLabel(
        'Entregada',
        icon: Icons.check_circle_outline,
        tone: StatusTone.positive,
      ),
      'NO_ENTREGADO' => const StatusLabel(
        'No se pudo entregar',
        icon: Icons.error_outline,
        tone: StatusTone.danger,
      ),
      'CANCELADO' => const StatusLabel(
        'Cancelada',
        icon: Icons.cancel_outlined,
        tone: StatusTone.danger,
      ),
      _ => _fallback(raw),
    };

/// Estado del cobro de un pedido, tal como lo ve el repartidor.
StatusLabel paymentStatusLabel(String? raw) =>
    switch (raw?.trim().toUpperCase()) {
      'PAGADO' => const StatusLabel(
        'Ya está pagado',
        detail: 'No hay que cobrar nada en la puerta.',
        icon: Icons.verified_outlined,
        tone: StatusTone.positive,
      ),
      'PENDIENTE' => const StatusLabel(
        'Falta cobrar',
        icon: Icons.payments_outlined,
        tone: StatusTone.warning,
      ),
      'CANCELADO' => const StatusLabel(
        'Pedido cancelado',
        icon: Icons.cancel_outlined,
        tone: StatusTone.danger,
      ),
      _ => _fallback(raw),
    };

/// Estado de un vehículo de reparto (`EstadoVehiculo` en el backend).
StatusLabel vehicleStatusLabel(String? raw) =>
    switch (raw?.trim().toUpperCase()) {
      'DISPONIBLE' => const StatusLabel(
        'Disponible',
        icon: Icons.check_circle_outline,
        tone: StatusTone.positive,
      ),
      'EN_USO' => const StatusLabel(
        'En uso',
        icon: Icons.directions_car_outlined,
        tone: StatusTone.progress,
      ),
      'MANTENIMIENTO' => const StatusLabel(
        'En mantenimiento',
        icon: Icons.build_outlined,
        tone: StatusTone.warning,
      ),
      'FUERA_DE_SERVICIO' => const StatusLabel(
        'Fuera de servicio',
        icon: Icons.report_problem_outlined,
        tone: StatusTone.danger,
      ),
      'INACTIVO' => const StatusLabel(
        'Inactivo',
        icon: Icons.pause_circle_outline,
        tone: StatusTone.neutral,
      ),
      _ => _fallback(raw),
    };

/// Forma de pago elegida en el checkout (`MetodoPagoCheckout` en el backend).
String paymentMethodLabel(String? raw) => switch (raw?.trim().toUpperCase()) {
  'PAGO_EN_LOCAL' => 'Paga al retirar',
  'PAGO_EFECTIVO_REPARTIDOR' => 'Efectivo al recibir',
  'TRANSFERENCIA_BANCARIA' => 'Transferencia bancaria',
  null || '' => 'Forma de pago no informada',
  _ => _fallback(raw).label,
};

/// Modalidad de entrega (`MetodoEntrega` en el backend).
String deliveryMethodLabel(String? raw) => switch (raw?.trim().toUpperCase()) {
  'RETIRO_TIENDA' => 'Retiro en la tienda',
  'ENVIO_DOMICILIO' => 'Envío a domicilio',
  null || '' => 'Entrega sin definir',
  _ => _fallback(raw).label,
};

/// Para un código que todavía no está traducido: se muestra legible en vez de
/// en mayúsculas con guiones bajos. Es una red de seguridad, no un reemplazo de
/// la traducción: si aparece, falta agregar el estado acá arriba.
StatusLabel _fallback(String? raw) {
  final value = raw?.trim() ?? '';
  if (value.isEmpty) {
    return const StatusLabel(
      'Sin estado',
      icon: Icons.help_outline,
      tone: StatusTone.neutral,
    );
  }
  final words = value.replaceAll('_', ' ').toLowerCase();
  return StatusLabel(
    words[0].toUpperCase() + words.substring(1),
    icon: Icons.info_outline,
    tone: StatusTone.neutral,
  );
}

/// Chip de estado, para que todos los estados se vean igual en toda la app.
class StatusChip extends StatelessWidget {
  const StatusChip({required this.status, super.key, this.compact = false});

  final StatusLabel status;
  final bool compact;

  @override
  Widget build(BuildContext context) {
    final color = status.color(context);
    return Container(
      padding: EdgeInsets.symmetric(
        horizontal: compact ? 8 : 10,
        vertical: compact ? 3 : 5,
      ),
      decoration: BoxDecoration(
        color: color.withValues(alpha: .12),
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: color.withValues(alpha: .4)),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(status.icon, size: compact ? 14 : 16, color: color),
          const SizedBox(width: 5),
          Flexible(
            child: Text(
              status.label,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style:
                  (compact
                          ? Theme.of(context).textTheme.labelSmall
                          : Theme.of(context).textTheme.labelMedium)
                      ?.copyWith(color: color, fontWeight: FontWeight.w600),
            ),
          ),
        ],
      ),
    );
  }
}
