import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';

import '../../../core/auth/operation_confirm.dart';
import '../../../core/di/providers.dart';
import '../../../core/errors/app_failure.dart';
import '../../../core/format/money.dart';
import '../../../core/format/status_labels.dart';
import '../../../core/native/document_service.dart';
import '../../../core/native/haptics_service.dart';
import '../../../core/widgets/async_states.dart';
import '../../../core/widgets/vuelta_al_inicio.dart';
import '../../checkout/application/checkout_providers.dart';
import 'payment_receipts.dart';

/// Estado en el que el backend todavía admite que el cliente anule el pedido.
/// Después de confirmarlo ya se tocó el stock real y la baja pasa por el local.
const String _cancellableStatus = 'P';
const String _cancelledStatus = 'X';

/// El unico metodo de pago que pide respaldo: los otros se cobran en persona.
const String _pagoConComprobante = 'TRANSFERENCIA_BANCARIA';

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
          loading: () => const AppLoading(label: 'Cargando tus pedidos'),
          error: (error, _) => AppErrorState(
            message:
                'No pudimos cargar tus pedidos. '
                'Revisá tu conexión e intentá de nuevo.',
            onRetry: () => ref.invalidate(ordersProvider),
          ),
          data: (items) => items.isEmpty
              ? _NoOrders(onBrowse: () => context.push('/catalog'))
              : ListView.separated(
                  padding: const EdgeInsets.all(12),
                  itemCount: items.length,
                  separatorBuilder: (context, index) =>
                      const SizedBox(height: 10),
                  itemBuilder: (context, index) {
                    final order = items[index];
                    final status = orderStatusLabel(order.status);
                    return Card(
                      child: ListTile(
                        leading: const Icon(Icons.receipt_long_outlined),
                        title: Text('Pedido N.º ${order.number}'),
                        subtitle: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            const SizedBox(height: 2),
                            Text(
                              DateFormat.yMMMd('es_PY')
                                  .add_Hm()
                                  .format(order.date.toLocal()),
                            ),
                            const SizedBox(height: 6),
                            Align(
                              alignment: Alignment.centerLeft,
                              child: StatusChip(status: status, compact: true),
                            ),
                          ],
                        ),
                        isThreeLine: true,
                        trailing: Text(
                          formatMoney(order.total, order.currency),
                          style: Theme.of(context).textTheme.titleSmall,
                        ),
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
  const OrderDetailPage({required this.orderId, super.key});
  final int orderId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final order = ref.watch(orderProvider(orderId));
    final theme = Theme.of(context);
    return VueltaAlInicio(
      child: Scaffold(
        appBar: AppBar(
          title: const Text('Detalle del pedido'),
          leading: VueltaAlInicio.flecha(context),
        ),
        body: order.when(
          loading: () => const AppLoading(label: 'Cargando el pedido'),
          error: (error, _) => AppErrorState(
            message: 'No pudimos cargar este pedido. Intentá de nuevo.',
            onRetry: () => ref.invalidate(orderProvider(orderId)),
          ),
          data: (data) {
            final details = data['detalles'] as List? ?? const [];
            final rawStatus = data['estado'] as String? ?? '';
            final status = orderStatusLabel(rawStatus);
            final currency = data['moneda']?.toString() ?? 'PYG';
            final shipment = ref.watch(shipmentProvider(orderId));

            return ListView(
              padding: const EdgeInsets.all(16),
              children: [
                Text(
                  'Pedido N.º ${data['numero']}',
                  style: theme.textTheme.headlineSmall,
                ),
                const SizedBox(height: 10),
                StatusChip(status: status),
                if (status.detail != null) ...[
                  const SizedBox(height: 8),
                  Text(
                    status.detail!,
                    style: theme.textTheme.bodyMedium?.copyWith(
                      color: theme.colorScheme.onSurfaceVariant,
                    ),
                  ),
                ],
                const Divider(height: 32),
                Text('Productos', style: theme.textTheme.titleSmall),
                const SizedBox(height: 4),
                for (final raw in details)
                  Builder(
                    builder: (context) {
                      final line = raw as Map;
                      final subtotal = (line['subtotal'] as num?)?.toDouble();
                      final quantity = (line['cantidad'] as num?)?.toInt() ?? 0;
                      return ListTile(
                        contentPadding: EdgeInsets.zero,
                        title: Text(
                          line['productoNombre']?.toString() ?? 'Producto',
                        ),
                        subtitle: Text(
                          '$quantity ${quantity == 1 ? 'unidad' : 'unidades'}',
                        ),
                        trailing: Text(
                          subtotal == null
                              ? ''
                              : formatMoney(subtotal, currency),
                        ),
                      );
                    },
                  ),
                const Divider(),
                ListTile(
                  contentPadding: EdgeInsets.zero,
                  title: Text('Total', style: theme.textTheme.titleMedium),
                  trailing: Text(
                    formatMoney(
                      (data['total'] as num?)?.toDouble() ?? 0,
                      currency,
                    ),
                    style: theme.textTheme.titleMedium,
                  ),
                ),
                // Un pedido cancelado seguía mostrando debajo «Pendiente de
                // preparación»: el envío queda en ese estado del lado del
                // servidor, pero al cliente le decía dos cosas opuestas en la
                // misma pantalla.
                if (rawStatus.trim().toUpperCase() != _cancelledStatus)
                  shipment.when(
                    // El envío es información complementaria: si no carga, no se
                    // muestra un error que tape el pedido en sí.
                    loading: () => const LinearProgressIndicator(),
                    error: (error, _) => const SizedBox.shrink(),
                    data: (value) {
                      final shipping = shipmentStatusLabel(
                        value['estado']?.toString(),
                      );
                      // El código de seguimiento aparecía solo, sin decir qué
                      // era: debajo del pedido «N.º 69» se leía «TS-69» y parecía
                      // un dato interno filtrado, no el número para consultar.
                      final code = value['codigoSeguimiento']?.toString();
                      final carrier = value['transportista']?.toString();
                      final tracking = code != null && code.trim().isNotEmpty
                          ? 'Código de seguimiento: ${code.trim()}'
                          : (carrier != null && carrier.trim().isNotEmpty
                                ? 'Lo lleva ${carrier.trim()}'
                                : null);
                      return Card(
                        child: ListTile(
                          leading: Icon(
                            shipping.icon,
                            color: shipping.color(context),
                          ),
                          title: Text(shipping.label),
                          subtitle: Text(
                            tracking ??
                                shipping.detail ??
                                'Envío en preparación',
                          ),
                        ),
                      );
                    },
                  ),
                // Los comprobantes solo tienen sentido si el pago fue por
                // transferencia y el pedido sigue en pie.
                if (data['metodoPago']?.toString() == _pagoConComprobante &&
                    rawStatus.trim().toUpperCase() != _cancelledStatus)
                  PaymentReceipts(orderId: orderId),
                const SizedBox(height: 12),
                // La factura solo aparece cuando el servidor confirma que existe:
                // ofrecerla antes del pago sería prometer algo que no está.
                if (data['facturaDisponible'] == true)
                  FilledButton.tonalIcon(
                    onPressed: () => _invoice(context, ref),
                    icon: const Icon(Icons.picture_as_pdf_outlined),
                    label: const Text('Descargar factura'),
                  ),
                if (rawStatus.trim().toUpperCase() == _cancellableStatus) ...[
                  const SizedBox(height: 8),
                  OutlinedButton.icon(
                    onPressed: () => _cancel(context, ref, data['numero']),
                    icon: const Icon(Icons.cancel_outlined),
                    label: const Text('Cancelar pedido'),
                  ),
                ],
              ],
            );
          },
        ),
      ),
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
