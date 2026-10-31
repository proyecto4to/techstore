import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';

import '../../../core/animacion/aparicion.dart';
import '../../../core/auth/operation_confirm.dart';
import '../../../core/di/providers.dart';
import '../../../core/diseno/medidas.dart';
import '../../../core/diseno/paleta.dart';
import '../../../core/errors/app_failure.dart';
import '../../../core/format/money.dart';
import '../../../core/format/status_labels.dart';
import '../../../core/native/document_service.dart';
import '../../../core/native/haptics_service.dart';
import '../../../core/widgets/async_states.dart';
import '../../../core/widgets/documento_page.dart';
import '../../../core/widgets/vuelta_al_inicio.dart';
import '../../chat/presentation/chat_page.dart';
import '../../checkout/application/checkout_providers.dart';
import '../../payments/application/payments_providers.dart';
import '../../payments/domain/payment_models.dart';
import '../../payments/presentation/receipt_widgets.dart';
import '../../payments/presentation/transfer_instructions.dart';
import '../domain/order_tracking.dart';

/// Estado en el que el backend todavía admite que el cliente anule el pedido.
/// Después de confirmarlo ya se tocó el stock real y la baja pasa por el local.
const String _cancellableStatus = 'P';

class OrdersPage extends ConsumerWidget {
  const OrdersPage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final orders = ref.watch(ordersProvider);
    return Scaffold(
      appBar: AppBar(title: const Text('Mis pedidos')),
      body: RefreshIndicator(
        onRefresh: () => ref.refresh(ordersProvider.future),
        child: orders.when(
          skipLoadingOnRefresh: true,
          loading: () => const _OrdersSkeleton(),
          error: (error, _) => AppErrorState(
            message:
                'No pudimos cargar tus pedidos. '
                'Revisá tu conexión e intentá de nuevo.',
            onRetry: () => ref.invalidate(ordersProvider),
          ),
          data: (items) => items.isEmpty
              ? _NoOrders(onBrowse: () => context.push('/catalog'))
              : ListView.separated(
                  padding: const EdgeInsets.fromLTRB(16, 12, 16, 32),
                  itemCount: items.length,
                  separatorBuilder: (context, index) =>
                      const SizedBox(height: 12),
                  itemBuilder: (context, index) {
                    final order = items[index];
                    final tracking = orderTracking(
                      estado: order.status,
                      estadoPago: order.paymentStatus,
                      metodoEntrega: order.deliveryMethod,
                    );
                    return Aparicion(
                      orden: index.clamp(0, 8),
                      child: _OrderCard(
                        number: order.number,
                        date: order.date,
                        total: formatMoney(order.total, order.currency),
                        status: tracking.summary,
                        delivery: order.deliveryMethod,
                        onTap: () => context.push('/orders/${order.id}'),
                      ),
                    );
                  },
                ),
        ),
      ),
    );
  }
}

/// La tarjeta de un pedido en la lista: el estado arriba, que es lo que se
/// viene a mirar, y debajo el número, la fecha y el total.
class _OrderCard extends StatelessWidget {
  const _OrderCard({
    required this.number,
    required this.date,
    required this.total,
    required this.status,
    required this.delivery,
    required this.onTap,
  });

  final int number;
  final DateTime date;
  final String total;
  final StatusLabel status;
  final String? delivery;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final color = status.color(context);
    final aDomicilio = delivery == 'ENVIO_DOMICILIO';
    return Material(
      color: Paleta.superficie,
      shape: RoundedRectangleBorder(
        borderRadius: Medidas.bordeTarjeta,
        side: const BorderSide(color: Paleta.borde),
      ),
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: onTap,
        child: IntrinsicHeight(
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              // La franja del color del estado: en una lista larga, el pedido
              // que necesita algo se encuentra sin leer.
              Container(width: 5, color: color),
              Expanded(
                child: Padding(
                  padding: const EdgeInsets.fromLTRB(14, 14, 10, 14),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        children: [
                          Expanded(
                            child: Text(
                              'Pedido N.º $number',
                              style: theme.textTheme.titleMedium,
                            ),
                          ),
                          Text(
                            total,
                            style: theme.textTheme.titleSmall?.copyWith(
                              fontWeight: FontWeight.w800,
                            ),
                          ),
                        ],
                      ),
                      const SizedBox(height: 4),
                      Row(
                        children: [
                          Icon(
                            aDomicilio
                                ? Icons.local_shipping_outlined
                                : Icons.storefront_outlined,
                            size: 14,
                            color: theme.colorScheme.onSurfaceVariant,
                          ),
                          const SizedBox(width: 4),
                          Expanded(
                            child: Text(
                              DateFormat(
                                "d 'de' MMMM, HH:mm",
                                'es',
                              ).format(date.toLocal()),
                              style: theme.textTheme.bodySmall?.copyWith(
                                color: theme.colorScheme.onSurfaceVariant,
                              ),
                            ),
                          ),
                        ],
                      ),
                      const SizedBox(height: 10),
                      StatusChip(status: status, compact: true),
                    ],
                  ),
                ),
              ),
              Padding(
                padding: const EdgeInsets.only(right: 8),
                child: Icon(
                  Icons.chevron_right,
                  color: theme.colorScheme.onSurfaceVariant,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

/// Mientras carga, la forma de la lista: se nota que algo viene y dónde.
class _OrdersSkeleton extends StatelessWidget {
  const _OrdersSkeleton();

  @override
  Widget build(BuildContext context) => ListView.separated(
    padding: const EdgeInsets.fromLTRB(16, 12, 16, 32),
    physics: const NeverScrollableScrollPhysics(),
    itemCount: 4,
    separatorBuilder: (_, _) => const SizedBox(height: 12),
    itemBuilder: (context, index) => Semantics(
      label: index == 0 ? 'Cargando tus pedidos' : null,
      child: Container(
        height: 104,
        decoration: BoxDecoration(
          color: Paleta.superficie,
          borderRadius: Medidas.bordeTarjeta,
          border: Border.all(color: Paleta.borde),
        ),
        padding: const EdgeInsets.all(16),
        child: const Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            _Hueso(ancho: 140, alto: 16),
            SizedBox(height: 10),
            _Hueso(ancho: 110, alto: 12),
            Spacer(),
            _Hueso(ancho: 120, alto: 22, radio: 20),
          ],
        ),
      ),
    ),
  );
}

class _Hueso extends StatelessWidget {
  const _Hueso({required this.ancho, required this.alto, this.radio = 6});

  final double ancho;
  final double alto;
  final double radio;

  @override
  Widget build(BuildContext context) => Container(
    width: ancho,
    height: alto,
    decoration: BoxDecoration(
      color: Paleta.superficieElevada,
      borderRadius: BorderRadius.circular(radio),
    ),
  );
}

class _NoOrders extends StatelessWidget {
  const _NoOrders({required this.onBrowse});

  final VoidCallback onBrowse;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return ListView(
      children: [
        const SizedBox(height: 120),
        Center(
          child: Padding(
            padding: const EdgeInsets.all(32),
            child: Column(
              children: [
                ExcludeSemantics(
                  child: Icon(
                    Icons.receipt_long_outlined,
                    size: 56,
                    color: theme.colorScheme.onSurfaceVariant,
                  ),
                ),
                const SizedBox(height: 12),
                Text(
                  'Todavía no hiciste ningún pedido',
                  style: theme.textTheme.titleSmall,
                ),
                const SizedBox(height: 8),
                Text(
                  'Acá vas a poder seguir el estado de tus compras.',
                  textAlign: TextAlign.center,
                  style: theme.textTheme.bodyMedium?.copyWith(
                    color: theme.colorScheme.onSurfaceVariant,
                  ),
                ),
                const SizedBox(height: 20),
                FilledButton.icon(
                  onPressed: onBrowse,
                  icon: const Icon(Icons.storefront_outlined),
                  label: const Text('Ver productos'),
                ),
              ],
            ),
          ),
        ),
      ],
    );
  }
}

class OrderDetailPage extends ConsumerWidget {
  const OrderDetailPage({
    required this.orderId,
    super.key,
    this.justPlaced = false,
  });

  final int orderId;

  /// Si se abre justo después de comprar: arriba va la confirmación.
  final bool justPlaced;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final order = ref.watch(orderProvider(orderId));
    return VueltaAlInicio(
      child: Scaffold(
        appBar: AppBar(
          title: const Text('Detalle del pedido'),
          leading: VueltaAlInicio.flecha(context),
        ),
        body: order.when(
          skipLoadingOnRefresh: true,
          loading: () => const AppLoading(label: 'Cargando el pedido'),
          error: (error, _) => AppErrorState(
            message: 'No pudimos cargar este pedido. Intentá de nuevo.',
            onRetry: () => ref.invalidate(orderProvider(orderId)),
          ),
          data: (data) => RefreshIndicator(
            onRefresh: () async {
              ref
                ..invalidate(shipmentProvider(orderId))
                ..invalidate(orderReceiptsProvider(orderId))
                ..invalidate(orderHistoryProvider(orderId))
                ..invalidate(orderProvider(orderId));
              await ref.read(orderProvider(orderId).future);
            },
            child: _OrderDetail(
              orderId: orderId,
              data: data,
              justPlaced: justPlaced,
            ),
          ),
        ),
      ),
    );
  }
}

class _OrderDetail extends ConsumerWidget {
  const _OrderDetail({
    required this.orderId,
    required this.data,
    required this.justPlaced,
  });

  final int orderId;
  final Map<String, Object?> data;
  final bool justPlaced;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final theme = Theme.of(context);
    final details = data['detalles'] as List? ?? const [];
    final rawStatus = (data['estado'] as String? ?? '').trim().toUpperCase();
    final currency = data['moneda']?.toString() ?? 'PYG';
    final payment = PaymentState.parse(data['estadoPago']?.toString());
    final delivery = data['metodoEntrega']?.toString();
    final aDomicilio = delivery == 'ENVIO_DOMICILIO';
    final cancelled = rawStatus == 'X';
    final shipment = cancelled ? null : ref.watch(shipmentProvider(orderId));
    final envio = shipment?.value;
    final tracking = orderTracking(
      estado: rawStatus,
      estadoPago: data['estadoPago']?.toString(),
      metodoEntrega: delivery,
      estadoEnvio: envio?['estado']?.toString(),
      motivoRechazo: data['pagoRechazoMotivo']?.toString(),
    );
    final number = data['numero'];
    final date = DateTime.tryParse(data['fecha']?.toString() ?? '');
    final code = envio?['codigoSeguimiento']?.toString().trim();
    final total = (data['total'] as num?)?.toDouble() ?? 0;
    final subtotal = (data['subtotalProductos'] as num?)?.toDouble();
    final shippingCost = (data['costoEnvio'] as num?)?.toDouble() ?? 0;

    final puedeCancelar =
        rawStatus == _cancellableStatus &&
        payment != PaymentState.inVerification;

    return ListView(
      padding: const EdgeInsets.fromLTRB(16, 12, 16, 40),
      children: [
        if (justPlaced) ...[
          _PedidoRecibido(number: number, payment: payment),
          const SizedBox(height: 16),
        ],
        Text('Pedido N.º $number', style: theme.textTheme.headlineSmall),
        const SizedBox(height: 4),
        Wrap(
          spacing: 10,
          runSpacing: 6,
          crossAxisAlignment: WrapCrossAlignment.center,
          children: [
            if (date != null)
              Text(
                DateFormat("d 'de' MMMM y, HH:mm", 'es').format(date.toLocal()),
                style: theme.textTheme.bodySmall?.copyWith(
                  color: theme.colorScheme.onSurfaceVariant,
                ),
              ),
            if (code != null && code.isNotEmpty)
              _CodigoDeSeguimiento(code: code),
          ],
        ),
        const SizedBox(height: 12),
        StatusChip(status: tracking.summary),
        if (tracking.summary.detail != null) ...[
          const SizedBox(height: 8),
          Text(
            tracking.summary.detail!,
            style: theme.textTheme.bodyMedium?.copyWith(
              color: theme.colorScheme.onSurfaceVariant,
            ),
          ),
        ],
        if (tracking.steps.isNotEmpty) ...[
          const SizedBox(height: 20),
          _Recorrido(steps: tracking.steps),
        ],
        if (!cancelled && data['metodoPago'] == 'TRANSFERENCIA_BANCARIA') ...[
          const SizedBox(height: 20),
          _PagoPorTransferencia(
            orderId: orderId,
            state: payment,
            amount: total,
            currency: currency,
            rejectionReason: data['pagoRechazoMotivo']?.toString(),
          ),
        ],
        const SizedBox(height: 20),
        _Seccion(
          titulo: aDomicilio ? 'Entrega a domicilio' : 'Retiro en la tienda',
          icono: aDomicilio
              ? Icons.local_shipping_outlined
              : Icons.storefront_outlined,
          child: aDomicilio
              ? _DireccionDelPedido(
                  direccion: data['direccionEntrega'] as Map?,
                  carrier: envio?['transportista']?.toString(),
                )
              : Text(
                  payment == PaymentState.notRequired
                      ? 'Lo pagás en la tienda cuando pasás a buscarlo.'
                      : 'Te avisamos cuando esté listo para retirar.',
                  style: theme.textTheme.bodyMedium,
                ),
        ),
        const SizedBox(height: 16),
        _Seccion(
          titulo: 'Productos',
          icono: Icons.inventory_2_outlined,
          child: Column(
            children: [
              for (final raw in details)
                Builder(
                  builder: (context) {
                    final line = raw as Map;
                    final lineSubtotal = (line['subtotal'] as num?)?.toDouble();
                    final quantity = (line['cantidad'] as num?)?.toInt() ?? 0;
                    return Padding(
                      padding: const EdgeInsets.symmetric(vertical: 6),
                      child: Row(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Container(
                            width: 28,
                            alignment: Alignment.center,
                            padding: const EdgeInsets.symmetric(vertical: 2),
                            decoration: BoxDecoration(
                              color: Paleta.superficieElevada,
                              borderRadius: BorderRadius.circular(6),
                            ),
                            child: Text(
                              '$quantity×',
                              style: theme.textTheme.labelSmall,
                            ),
                          ),
                          const SizedBox(width: 10),
                          Expanded(
                            child: Text(
                              line['productoNombre']?.toString() ?? 'Producto',
                              style: theme.textTheme.bodyMedium,
                            ),
                          ),
                          const SizedBox(width: 8),
                          Text(
                            lineSubtotal == null
                                ? ''
                                : formatMoney(lineSubtotal, currency),
                            style: theme.textTheme.bodyMedium,
                          ),
                        ],
                      ),
                    );
                  },
                ),
              const Divider(height: 24),
              if (subtotal != null)
                _Fila('Productos', formatMoney(subtotal, currency)),
              _Fila(
                'Envío',
                aDomicilio ? formatMoney(shippingCost, currency) : 'Sin costo',
              ),
              const SizedBox(height: 4),
              _Fila('Total', formatMoney(total, currency), destacado: true),
            ],
          ),
        ),
        const SizedBox(height: 16),
        _Historial(orderId: orderId),
        const SizedBox(height: 20),
        OutlinedButton.icon(
          onPressed: () => abrirConsultaDelPedido(context, ref, orderId),
          icon: const Icon(Icons.support_agent_outlined),
          label: const Text('Consultar por este pedido'),
        ),
        // La factura solo aparece cuando el servidor confirma que existe:
        // ofrecerla antes del pago sería prometer algo que no está.
        if (data['facturaDisponible'] == true) ...[
          const SizedBox(height: 8),
          FilledButton.tonalIcon(
            onPressed: () => _invoice(context, ref),
            icon: const Icon(Icons.picture_as_pdf_outlined),
            label: const Text('Descargar factura'),
          ),
        ],
        if (puedeCancelar) ...[
          const SizedBox(height: 8),
          TextButton.icon(
            onPressed: () => _cancel(context, ref, number),
            style: TextButton.styleFrom(
              foregroundColor: theme.colorScheme.error,
            ),
            icon: const Icon(Icons.cancel_outlined),
            label: const Text('Cancelar pedido'),
          ),
        ],
      ],
    );
  }

  /// Cancelar es irreversible: se confirma en pantalla, se valida la identidad
  /// cuando el usuario activó la guarda biométrica y se avisa si el backend
  /// rechaza la baja, en lugar de dejar la excepción sin manejar.
  Future<void> _cancel(
    BuildContext context,
    WidgetRef ref,
    Object? orderNumber,
  ) async {
    final haptics = ref.read(hapticsServiceProvider);
    final accepted = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('¿Cancelar este pedido?'),
        content: Text(
          'El pedido N.º $orderNumber deja de procesarse y los productos '
          'vuelven a quedar disponibles para otros clientes. '
          'No se puede reabrir.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Volver'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('Cancelar pedido'),
          ),
        ],
      ),
    );
    if (accepted != true) return;
    if (!context.mounted) return;
    final confirmed = await confirmarOperacion(
      context,
      ref,
      reason: 'Confirmá la cancelación del pedido',
      titulo: 'Confirmar cancelación',
    );
    if (!confirmed) {
      await haptics.play(HapticCue.error);
      return;
    }
    try {
      await ref.read(ordersRepositoryProvider).cancel(orderId);
      await haptics.play(HapticCue.warning);
      ref.invalidate(orderProvider(orderId));
      ref.invalidate(ordersProvider);
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Tu pedido quedó cancelado.')),
        );
      }
    } on AppFailure catch (failure) {
      await haptics.play(HapticCue.error);
      if (context.mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text(failure.message)));
      }
    }
  }

  Future<void> _invoice(BuildContext context, WidgetRef ref) async {
    final action = await showModalBottomSheet<DocumentAction>(
      context: context,
      showDragHandle: true,
      builder: (context) => SafeArea(
        child: Wrap(
          children: [
            for (final item in const [
              (DocumentAction.open, Icons.open_in_new, 'Abrir la factura'),
              (DocumentAction.share, Icons.share_outlined, 'Compartir'),
              (DocumentAction.print, Icons.print_outlined, 'Imprimir'),
            ])
              ListTile(
                leading: Icon(item.$2),
                title: Text(item.$3),
                onTap: () => Navigator.pop(context, item.$1),
              ),
          ],
        ),
      ),
    );
    if (action == null || !context.mounted) return;
    try {
      await ref
          .read(documentServiceProvider)
          .handle(
            '/api/v1/pedidos/$orderId/factura/pdf',
            'factura-pedido-$orderId.pdf',
            action,
          );
    } on AppFailure catch (failure) {
      if (context.mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text(failure.message)));
      }
    } on Object {
      // El detalle técnico ya queda en el log del cliente HTTP; al usuario se
      // le dice qué pasó y qué puede hacer, sin volcarle la excepción.
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text(
              'No pudimos abrir la factura. Revisá tu conexión e intentá de nuevo.',
            ),
          ),
        );
      }
    }
  }
}

/// Abre la conversación con la tienda sobre este pedido, o la empieza.
///
/// Si ya hubo una consulta por el pedido, se retoma esa: dos hilos sobre la
/// misma compra se pierden entre sí.
Future<void> abrirConsultaDelPedido(
  BuildContext context,
  WidgetRef ref,
  int orderId,
) async {
  List<Map<String, Object?>> conversaciones;
  try {
    conversaciones = await ref.read(conversationsProvider.future);
  } on Object {
    conversaciones = const [];
  }
  if (!context.mounted) return;
  final existente = conversaciones
      .where((c) => (c['pedidoId'] as num?)?.toInt() == orderId)
      .firstOrNull;
  await context.push(
    existente == null
        ? '/chat/nuevo?pedido=$orderId'
        : '/chat/${existente['id']}',
  );
}

/// Lo primero que se ve después de comprar.
class _PedidoRecibido extends StatelessWidget {
  const _PedidoRecibido({required this.number, required this.payment});

  final Object? number;
  final PaymentState payment;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final siguiente = switch (payment) {
      PaymentState.inVerification =>
        'Estamos verificando tu transferencia. Te avisamos por notificación '
            'apenas la confirmemos y empezamos a prepararlo.',
      PaymentState.waitingReceipt =>
        'Falta el comprobante de la transferencia: adjuntalo abajo para que '
            'podamos verificarla.',
      _ => 'Te avisamos por notificación cuando esté listo para retirar.',
    };
    return Aparicion(
      escala: .96,
      child: Container(
        padding: const EdgeInsets.all(18),
        decoration: BoxDecoration(
          borderRadius: Medidas.bordeTarjeta,
          gradient: const LinearGradient(
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
            colors: [Paleta.azulElectrico, Paleta.azulProfundo],
          ),
        ),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Icon(Icons.check_circle, size: 40, color: Paleta.texto),
            const SizedBox(width: 14),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    '¡Recibimos tu pedido!',
                    style: theme.textTheme.titleLarge?.copyWith(
                      color: Paleta.texto,
                      fontWeight: FontWeight.w800,
                    ),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    'Tu número de seguimiento es el $number.',
                    style: theme.textTheme.bodyMedium?.copyWith(
                      color: Paleta.texto,
                    ),
                  ),
                  const SizedBox(height: 8),
                  Text(
                    siguiente,
                    style: theme.textTheme.bodySmall?.copyWith(
                      color: Paleta.texto.withValues(alpha: .85),
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _CodigoDeSeguimiento extends StatelessWidget {
  const _CodigoDeSeguimiento({required this.code});

  final String code;

  @override
  Widget build(BuildContext context) => ActionChip(
    avatar: const Icon(Icons.qr_code_scanner, size: 16),
    label: Text('Seguimiento $code'),
    tooltip: 'Copiar el código de seguimiento',
    onPressed: () {
      Clipboard.setData(ClipboardData(text: code));
      ScaffoldMessenger.of(context)
        ..hideCurrentSnackBar()
        ..showSnackBar(const SnackBar(content: Text('Código copiado')));
    },
  );
}

/// Los pasos del pedido, de arriba hacia abajo, con el actual resaltado.
class _Recorrido extends StatelessWidget {
  const _Recorrido({required this.steps});

  final List<TrackingStep> steps;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final marca = theme.colorScheme;
    return Container(
      padding: const EdgeInsets.fromLTRB(14, 14, 14, 6),
      decoration: BoxDecoration(
        color: Paleta.superficie,
        borderRadius: Medidas.bordeTarjeta,
        border: Border.all(color: Paleta.borde),
      ),
      child: Column(
        children: [
          for (var i = 0; i < steps.length; i++)
            _Paso(
              step: steps[i],
              last: i == steps.length - 1,
              colorHecho: Paleta.exito,
              colorActual: marca.primary,
              colorFallido: marca.error,
            ),
        ],
      ),
    );
  }
}

class _Paso extends StatelessWidget {
  const _Paso({
    required this.step,
    required this.last,
    required this.colorHecho,
    required this.colorActual,
    required this.colorFallido,
  });

  final TrackingStep step;
  final bool last;
  final Color colorHecho;
  final Color colorActual;
  final Color colorFallido;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final color = switch (step.state) {
      EstadoDePaso.done => colorHecho,
      EstadoDePaso.current => colorActual,
      EstadoDePaso.failed => colorFallido,
      EstadoDePaso.pending => Paleta.bordeDeControl,
    };
    final icono = switch (step.state) {
      EstadoDePaso.done => Icons.check,
      EstadoDePaso.failed => Icons.priority_high,
      _ => step.icon,
    };
    final activo =
        step.state == EstadoDePaso.current || step.state == EstadoDePaso.failed;
    return Semantics(
      label:
          '${step.label}: ${switch (step.state) {
            EstadoDePaso.done => 'hecho',
            EstadoDePaso.current => 'en curso',
            EstadoDePaso.failed => 'con un problema',
            EstadoDePaso.pending => 'pendiente',
          }}',
      child: IntrinsicHeight(
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            SizedBox(
              width: 32,
              child: Column(
                children: [
                  Container(
                    width: activo ? 30 : 26,
                    height: activo ? 30 : 26,
                    decoration: BoxDecoration(
                      shape: BoxShape.circle,
                      color: step.state == EstadoDePaso.pending
                          ? Colors.transparent
                          : color.withValues(alpha: activo ? 1 : .18),
                      border: Border.all(color: color, width: 2),
                    ),
                    child: Icon(
                      icono,
                      size: 15,
                      color: activo
                          ? Paleta.tinta
                          : step.state == EstadoDePaso.pending
                          ? Paleta.textoSecundario
                          : color,
                    ),
                  ),
                  if (!last)
                    Expanded(
                      child: Container(
                        width: 2,
                        margin: const EdgeInsets.symmetric(vertical: 2),
                        color: step.state == EstadoDePaso.done
                            ? colorHecho.withValues(alpha: .6)
                            : Paleta.borde,
                      ),
                    ),
                ],
              ),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Padding(
                padding: EdgeInsets.only(top: 4, bottom: last ? 8 : 16),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      step.label,
                      style: theme.textTheme.bodyMedium?.copyWith(
                        fontWeight: activo ? FontWeight.w800 : FontWeight.w500,
                        color: step.state == EstadoDePaso.pending
                            ? Paleta.textoSecundario
                            : null,
                      ),
                    ),
                    if (step.detail != null) ...[
                      const SizedBox(height: 2),
                      Text(
                        step.detail!,
                        style: theme.textTheme.bodySmall?.copyWith(
                          color: step.state == EstadoDePaso.failed
                              ? colorFallido
                              : Paleta.textoSecundario,
                        ),
                      ),
                    ],
                  ],
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

/// El pago por transferencia: en qué está, los comprobantes y qué hacer.
class _PagoPorTransferencia extends ConsumerStatefulWidget {
  const _PagoPorTransferencia({
    required this.orderId,
    required this.state,
    required this.amount,
    required this.currency,
    this.rejectionReason,
  });

  final int orderId;
  final PaymentState state;
  final double amount;
  final String currency;
  final String? rejectionReason;

  @override
  ConsumerState<_PagoPorTransferencia> createState() =>
      _PagoPorTransferenciaState();
}

class _PagoPorTransferenciaState extends ConsumerState<_PagoPorTransferencia> {
  double? _progreso;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final receipts = ref.watch(orderReceiptsProvider(widget.orderId));
    final (titulo, icono, color) = switch (widget.state) {
      PaymentState.approved => ('Pago aprobado', Icons.verified, Paleta.exito),
      PaymentState.inVerification => (
        'Verificando tu transferencia',
        Icons.fact_check_outlined,
        Paleta.cian,
      ),
      PaymentState.rejected => (
        'No pudimos validar el pago',
        Icons.report_gmailerrorred_outlined,
        theme.colorScheme.error,
      ),
      _ => ('Falta el comprobante', Icons.upload_file_outlined, Paleta.aviso),
    };
    return _Seccion(
      titulo: 'Pago por transferencia',
      icono: Icons.account_balance_outlined,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Row(
            children: [
              Icon(icono, color: color, size: 20),
              const SizedBox(width: 8),
              Expanded(
                child: Text(
                  titulo,
                  style: theme.textTheme.titleSmall?.copyWith(color: color),
                ),
              ),
            ],
          ),
          if (widget.state == PaymentState.rejected &&
              widget.rejectionReason != null) ...[
            const SizedBox(height: 8),
            Container(
              padding: const EdgeInsets.all(10),
              decoration: BoxDecoration(
                color: Paleta.errorHundido,
                borderRadius: BorderRadius.circular(Medidas.radioChico),
              ),
              child: Text(
                'Motivo: ${widget.rejectionReason}',
                style: theme.textTheme.bodyMedium,
              ),
            ),
          ],
          const SizedBox(height: 12),
          receipts.when(
            loading: () => const LinearProgressIndicator(),
            error: (_, _) => Text(
              'No pudimos cargar los comprobantes.',
              style: theme.textTheme.bodySmall,
            ),
            data: (lista) => lista.isEmpty
                ? Text(
                    'Todavía no adjuntaste el comprobante.',
                    style: theme.textTheme.bodyMedium?.copyWith(
                      color: theme.colorScheme.onSurfaceVariant,
                    ),
                  )
                : Wrap(
                    spacing: 12,
                    runSpacing: 12,
                    children: [
                      for (final receipt in lista)
                        _ComprobanteDelPedido(
                          orderId: widget.orderId,
                          receipt: receipt,
                        ),
                    ],
                  ),
          ),
          if (widget.state.acceptsReceipt) ...[
            const SizedBox(height: 14),
            if (_progreso != null)
              LinearProgressIndicator(value: _progreso! <= 0 ? null : _progreso)
            else
              FilledButton.icon(
                onPressed: _adjuntar,
                icon: const Icon(Icons.upload_file_outlined),
                label: Text(
                  widget.state == PaymentState.rejected
                      ? 'Cargar un nuevo comprobante'
                      : 'Adjuntar comprobante',
                ),
              ),
            const SizedBox(height: 4),
            TextButton.icon(
              onPressed: () => _verDatos(context),
              icon: const Icon(Icons.account_balance_wallet_outlined),
              label: const Text('Ver los datos para transferir'),
            ),
          ],
        ],
      ),
    );
  }

  Future<void> _adjuntar() async {
    final source = await pickReceiptSource(context);
    if (source == null || !mounted) return;
    try {
      final picked = await ref.read(receiptPickerProvider).pick(source);
      if (picked == null || !mounted) return;
      setState(() => _progreso = 0);
      await ref
          .read(paymentsRepositoryProvider)
          .uploadOrderReceipt(
            widget.orderId,
            picked,
            onProgress: (p) {
              if (mounted) setState(() => _progreso = p);
            },
          );
      ref
        ..invalidate(orderReceiptsProvider(widget.orderId))
        ..invalidate(orderProvider(widget.orderId))
        ..invalidate(orderHistoryProvider(widget.orderId))
        ..invalidate(ordersProvider);
      unawaited(ref.read(hapticsServiceProvider).play(HapticCue.success));
      _avisar('Comprobante enviado. Lo vamos a revisar enseguida.');
    } on AppFailure catch (failure) {
      _avisar(failure.message);
    } finally {
      if (mounted) setState(() => _progreso = null);
    }
  }

  Future<void> _verDatos(BuildContext context) => showModalBottomSheet<void>(
    context: context,
    isScrollControlled: true,
    showDragHandle: true,
    builder: (context) => Consumer(
      builder: (context, ref, _) {
        final details = ref.watch(transferDetailsProvider);
        return SafeArea(
          child: SingleChildScrollView(
            padding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
            child: details.when(
              loading: () => const Padding(
                padding: EdgeInsets.all(32),
                child: Center(child: CircularProgressIndicator()),
              ),
              error: (_, _) =>
                  const Text('No pudimos traer los datos para transferir.'),
              data: (d) => TransferInstructions(
                details: d,
                amount: widget.amount,
                currency: widget.currency,
              ),
            ),
          ),
        );
      },
    ),
  );

  void _avisar(String mensaje) {
    if (!mounted) return;
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text(mensaje)));
  }
}

class _ComprobanteDelPedido extends ConsumerWidget {
  const _ComprobanteDelPedido({required this.orderId, required this.receipt});

  final int orderId;
  final PaymentReceipt receipt;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final bytes = receipt.isPdf
        ? null
        : ref.watch(orderReceiptFileProvider((orderId, receipt.id))).value;
    return SizedBox(
      width: 104,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          ReceiptThumbnail(
            isPdf: receipt.isPdf,
            bytes: bytes,
            loading: !receipt.isPdf && bytes == null,
            size: 104,
            onTap: () {
              if (receipt.isPdf) {
                Navigator.of(context).push<void>(
                  MaterialPageRoute(
                    builder: (_) => DocumentoPage(
                      titulo: 'Comprobante del pago',
                      endpoint:
                          '/api/v1/pedidos/$orderId/comprobantes/${receipt.id}/archivo',
                      nombreDeArchivo: 'comprobante-pedido-$orderId.pdf',
                      sePuedeImprimir: false,
                    ),
                  ),
                );
              } else if (bytes != null) {
                showReceiptImage(context, bytes);
              }
            },
          ),
          const SizedBox(height: 6),
          ReceiptReviewBadge(review: receipt.review),
        ],
      ),
    );
  }
}

class _DireccionDelPedido extends StatelessWidget {
  const _DireccionDelPedido({required this.direccion, this.carrier});

  final Map? direccion;
  final String? carrier;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final d = direccion;
    if (d == null) {
      return Text(
        'Sin dirección registrada.',
        style: theme.textTheme.bodyMedium,
      );
    }
    String? campo(String clave) {
      final valor = d[clave]?.toString().trim();
      return valor == null || valor.isEmpty ? null : valor;
    }

    final calle = [
      campo('direccionLinea1'),
      campo('numeroCasa'),
      campo('barrio'),
      campo('ciudad'),
    ].whereType<String>().join(', ');
    final secundario = theme.textTheme.bodySmall?.copyWith(
      color: theme.colorScheme.onSurfaceVariant,
    );
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          calle.isEmpty ? 'Ubicación marcada en el mapa' : calle,
          style: theme.textTheme.bodyMedium,
        ),
        const SizedBox(height: 4),
        if (campo('nombreDestinatario') != null)
          Text(
            'Recibe ${campo('nombreDestinatario')}'
            '${campo('telefono') == null ? '' : ' · ${campo('telefono')}'}',
            style: secundario,
          ),
        if (campo('referencia') != null)
          Text('Referencia: ${campo('referencia')}', style: secundario),
        if (carrier != null && carrier!.trim().isNotEmpty)
          Text('Lo lleva ${carrier!.trim()}', style: secundario),
      ],
    );
  }
}

/// La línea de tiempo del pedido, plegada: es para quien quiere el detalle.
class _Historial extends ConsumerWidget {
  const _Historial({required this.orderId});

  final int orderId;

  static String _titulo(String tipo) => switch (tipo) {
    'CREADO' => 'Pedido recibido',
    'COMPROBANTE_ADJUNTO' => 'Comprobante adjunto',
    'COMPROBANTE_QUITADO' => 'Comprobante quitado',
    'PAGO_APROBADO' => 'Pago aprobado',
    'PAGO_RECHAZADO' => 'Pago rechazado',
    'CONFIRMADO' => 'Pedido confirmado',
    'CANCELADO' => 'Pedido cancelado',
    'ENTREGADO' => 'Pedido entregado',
    _ => 'Actualización',
  };

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final theme = Theme.of(context);
    final history = ref.watch(orderHistoryProvider(orderId));
    final eventos = history.value ?? const <OrderEvent>[];
    if (eventos.isEmpty) return const SizedBox.shrink();
    return Container(
      decoration: BoxDecoration(
        color: Paleta.superficie,
        borderRadius: Medidas.bordeTarjeta,
        border: Border.all(color: Paleta.borde),
      ),
      child: Theme(
        data: theme.copyWith(dividerColor: Colors.transparent),
        child: ExpansionTile(
          leading: const Icon(Icons.history),
          title: const Text('Actividad del pedido'),
          subtitle: Text(
            '${eventos.length} ${eventos.length == 1 ? 'movimiento' : 'movimientos'}',
          ),
          childrenPadding: const EdgeInsets.fromLTRB(16, 0, 16, 12),
          children: [
            for (final evento in eventos.reversed)
              ListTile(
                dense: true,
                contentPadding: EdgeInsets.zero,
                title: Text(_titulo(evento.type)),
                subtitle: Text(
                  [
                    DateFormat(
                      "d MMM, HH:mm",
                      'es',
                    ).format(evento.date.toLocal()),
                    if (evento.detail != null &&
                        evento.type == 'PAGO_RECHAZADO')
                      evento.detail!,
                  ].join(' · '),
                ),
              ),
          ],
        ),
      ),
    );
  }
}

class _Seccion extends StatelessWidget {
  const _Seccion({
    required this.titulo,
    required this.icono,
    required this.child,
  });

  final String titulo;
  final IconData icono;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Paleta.superficie,
        borderRadius: Medidas.bordeTarjeta,
        border: Border.all(color: Paleta.borde),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Row(
            children: [
              Icon(icono, size: 18, color: Paleta.textoSecundario),
              const SizedBox(width: 8),
              Text(titulo, style: theme.textTheme.titleSmall),
            ],
          ),
          const SizedBox(height: 12),
          child,
        ],
      ),
    );
  }
}

class _Fila extends StatelessWidget {
  const _Fila(this.etiqueta, this.valor, {this.destacado = false});

  final String etiqueta;
  final String valor;
  final bool destacado;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final estilo = destacado
        ? theme.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w800)
        : theme.textTheme.bodyMedium?.copyWith(
            color: theme.colorScheme.onSurfaceVariant,
          );
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 2),
      child: Row(
        children: [
          Expanded(child: Text(etiqueta, style: estilo)),
          Text(valor, style: estilo),
        ],
      ),
    );
  }
}
