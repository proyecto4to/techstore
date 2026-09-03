import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';

import '../../../core/di/providers.dart';
import '../../../core/errors/app_failure.dart';
import '../../../core/native/document_service.dart';
import '../../../core/native/haptics_service.dart';
import '../../checkout/application/checkout_providers.dart';

class OrdersPage extends ConsumerWidget {
  const OrdersPage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final orders = ref.watch(ordersProvider);
    return Scaffold(
      appBar: AppBar(title: const Text('Pedidos')),
      body: RefreshIndicator(
        onRefresh: () => ref.refresh(ordersProvider.future),
        child: orders.when(
          loading: () => const Center(child: CircularProgressIndicator()),
          error: (error, _) => ListView(
            children: [
              const SizedBox(height: 180),
              Center(
                child: FilledButton(
                  onPressed: () => ref.invalidate(ordersProvider),
                  child: const Text('Reintentar'),
                ),
              ),
            ],
          ),
          data: (items) => items.isEmpty
              ? ListView(
                  children: const [
                    SizedBox(height: 180),
                    Center(child: Text('Sin pedidos')),
                  ],
                )
              : ListView.builder(
                  padding: const EdgeInsets.all(12),
                  itemCount: items.length,
                  itemBuilder: (context, index) {
                    final order = items[index];
                    return Card(
                      child: ListTile(
                        leading: const Icon(Icons.receipt_long_outlined),
                        title: Text('Pedido #${order.number}'),
                        subtitle: Text(
                          '${DateFormat.yMd('es_PY').add_Hm().format(order.date.toLocal())} · ${order.status}',
                        ),
                        trailing: Text(_money(order.total, order.currency)),
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

class OrderDetailPage extends ConsumerWidget {
  const OrderDetailPage({required this.orderId, super.key});
  final int orderId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final order = ref.watch(orderProvider(orderId));
    return Scaffold(
      appBar: AppBar(title: const Text('Detalle del pedido')),
      body: order.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (error, _) =>
            const Center(child: Text('No se pudo cargar el pedido')),
        data: (data) {
          final details = data['detalles'] as List? ?? const [];
          final status = data['estado'] as String? ?? '';
          final shipment = ref.watch(shipmentProvider(orderId));
          return ListView(
            padding: const EdgeInsets.all(16),
            children: [
              Text(
                'Pedido #${data['numero']}',
                style: Theme.of(context).textTheme.headlineSmall,
              ),
              Text(status, style: Theme.of(context).textTheme.titleMedium),
              const Divider(height: 32),
              for (final raw in details)
                Builder(
                  builder: (context) {
                    final line = raw as Map;
                    return ListTile(
                      title: Text(
                        line['productoNombre']?.toString() ?? 'Producto',
                      ),
                      subtitle: Text('Cantidad: ${line['cantidad']}'),
                      trailing: Text(
                        '${line['subtotal'] ?? ''} ${data['moneda'] ?? ''}',
                      ),
                    );
                  },
                ),
              const Divider(),
              ListTile(
                title: const Text('Total'),
                trailing: Text('${data['total']} ${data['moneda']}'),
              ),
              shipment.when(
                loading: () => const LinearProgressIndicator(),
                error: (error, _) => const SizedBox.shrink(),
                data: (value) => Card(
                  child: ListTile(
                    leading: const Icon(Icons.local_shipping_outlined),
                    title: Text(value['estado']?.toString() ?? 'Envío'),
                    subtitle: Text(
                      value['codigoSeguimiento']?.toString() ??
                          value['transportista']?.toString() ??
                          'Seguimiento disponible',
                    ),
                  ),
                ),
              ),
              if (status == 'Pendiente' || status == 'PENDIENTE')
                OutlinedButton.icon(
                  onPressed: () => _cancel(context, ref),
                  icon: const Icon(Icons.cancel_outlined),
                  label: const Text('Cancelar pedido'),
                ),
              if (data['facturaDisponible'] == true)
                FilledButton.tonalIcon(
                  onPressed: () => _invoice(context, ref),
                  icon: const Icon(Icons.picture_as_pdf_outlined),
                  label: const Text('Factura PDF'),
                ),
            ],
          );
        },
      ),
    );
  }

  /// Cancelar es irreversible: se confirma en pantalla, se valida la identidad
  /// cuando el usuario activó la guarda biométrica y se avisa si el backend
  /// rechaza la baja, en lugar de dejar la excepción sin manejar.
  Future<void> _cancel(BuildContext context, WidgetRef ref) async {
    final haptics = ref.read(hapticsServiceProvider);
    final accepted = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Cancelar pedido'),
        content: Text('El pedido #$orderId se cancelará y no podrá reabrirse.'),
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
    final confirmed = await ref
        .read(biometricServiceProvider)
        .confirmOperation(reason: 'Confirmá la cancelación del pedido');
    if (!confirmed) {
      await haptics.play(HapticCue.error);
      return;
    }
    try {
      await ref.read(ordersRepositoryProvider).cancel(orderId);
      await haptics.play(HapticCue.warning);
      ref.invalidate(orderProvider(orderId));
      ref.invalidate(ordersProvider);
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
      builder: (context) => SafeArea(
        child: Wrap(
          children: [
            for (final item in const [
              (DocumentAction.open, Icons.open_in_new, 'Abrir'),
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
    } catch (error) {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('No se pudo abrir la factura: $error')),
        );
      }
    }
  }
}

String _money(double value, String currency) => NumberFormat.currency(
  locale: 'es_PY',
  symbol: currency == 'PYG' ? '₲' : currency,
  decimalDigits: currency == 'PYG' ? 0 : 2,
).format(value);
