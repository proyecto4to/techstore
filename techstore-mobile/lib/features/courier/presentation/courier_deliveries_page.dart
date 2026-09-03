import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';
import 'package:url_launcher/url_launcher.dart';

import '../../../core/errors/app_failure.dart';
import '../application/courier_providers.dart';

class CourierDeliveriesPage extends ConsumerWidget {
  const CourierDeliveriesPage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) => DefaultTabController(
    length: 2,
    child: Scaffold(
      appBar: AppBar(
        title: const Text('Mis entregas'),
        bottom: const TabBar(
          tabs: [
            Tab(text: 'Por estado'),
            Tab(text: 'Por cliente'),
          ],
        ),
      ),
      body: const TabBarView(children: [_ByState(), _ByClient()]),
    ),
  );
}

class _ByState extends ConsumerWidget {
  const _ByState();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final deliveries = ref.watch(courierDeliveriesProvider);
    return RefreshIndicator(
      onRefresh: () => ref.refresh(courierDeliveriesProvider.future),
      child: deliveries.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (error, _) => _retry(
          'No se pudieron cargar las entregas',
          () => ref.invalidate(courierDeliveriesProvider),
        ),
        data: (items) {
          if (items.isEmpty) return _empty('No tenés entregas asignadas');
          final groups = <String, List<Map<String, Object?>>>{};
          for (final item in items) {
            groups.putIfAbsent('${item['estadoEntrega']}', () => []).add(item);
          }
          return ListView(
            padding: const EdgeInsets.all(12),
            children: [
              for (final state in _stateOrder)
                if (groups[state]?.isNotEmpty ?? false) ...[
                  Padding(
                    padding: const EdgeInsets.fromLTRB(4, 12, 4, 6),
                    child: Text(
                      '${_stateLabel(state)} · ${groups[state]!.length}',
                      style: Theme.of(context).textTheme.titleMedium,
                    ),
                  ),
                  for (final item in groups[state]!) _DeliveryCard(item: item),
                ],
            ],
          );
        },
      ),
    );
  }
}

class _ByClient extends ConsumerWidget {
  const _ByClient();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final clients = ref.watch(courierClientsProvider);
    return RefreshIndicator(
      onRefresh: () => ref.refresh(courierClientsProvider.future),
      child: clients.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (error, _) => _retry(
          'No se pudo agrupar por cliente',
          () => ref.invalidate(courierClientsProvider),
        ),
        data: (groups) => groups.isEmpty
            ? _empty('No hay entregas activas')
            : ListView.builder(
                padding: const EdgeInsets.all(12),
                itemCount: groups.length,
                itemBuilder: (context, index) {
                  final group = groups[index];
                  final raw = group['entregas'] as List? ?? const [];
                  final deliveries = raw
                      .map((item) => Map<String, Object?>.from(item as Map))
                      .toList(growable: false);
                  return Card(
                    child: ExpansionTile(
                      leading: const CircleAvatar(
                        child: Icon(Icons.person_outline),
                      ),
                      title: Text('${group['cliente']}'),
                      subtitle: Text(
                        '${group['direccion'] ?? 'Sin dirección'}\n${deliveries.length} pedido(s) en esta ubicación',
                      ),
                      children: [
                        for (final item in deliveries)
                          ListTile(
                            title: Text('Pedido #${item['pedidoNumero']}'),
                            subtitle: Text(
                              _stateLabel('${item['estadoEntrega']}'),
                            ),
                            trailing: Text(
                              _money(item['importe'], '${item['moneda']}'),
                            ),
                            onTap: () => context.push(
                              '/courier/deliveries/${item['id']}',
                            ),
                          ),
                      ],
                    ),
                  );
                },
              ),
      ),
    );
  }
}

class _DeliveryCard extends StatelessWidget {
  const _DeliveryCard({required this.item});
  final Map<String, Object?> item;

  @override
  Widget build(BuildContext context) => Card(
    child: ListTile(
      leading: CircleAvatar(child: Text('${item['ordenEntrega'] ?? '—'}')),
      title: Text('Pedido #${item['pedidoNumero']} · ${item['cliente']}'),
      subtitle: Text(
        '${item['direccion'] ?? 'Sin dirección'}\n'
        '${item['formaPago'] ?? 'Pago no informado'} · ${item['estadoPago']}',
      ),
      isThreeLine: true,
      trailing: const Icon(Icons.chevron_right),
      onTap: () => context.push('/courier/deliveries/${item['id']}'),
    ),
  );
}

class CourierDeliveryDetailPage extends ConsumerStatefulWidget {
  const CourierDeliveryDetailPage({required this.assignmentId, super.key});
  final int assignmentId;

  @override
  ConsumerState<CourierDeliveryDetailPage> createState() =>
      _CourierDeliveryDetailPageState();
}

class _CourierDeliveryDetailPageState
    extends ConsumerState<CourierDeliveryDetailPage> {
  bool _busy = false;

  @override
  Widget build(BuildContext context) {
    final deliveries = ref.watch(courierDeliveriesProvider);
    return Scaffold(
      appBar: AppBar(title: const Text('Detalle de entrega')),
      body: deliveries.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (error, _) => _retry(
          'No se pudo cargar la entrega',
          () => ref.invalidate(courierDeliveriesProvider),
        ),
        data: (items) {
          final matches = items.where(
            (item) => item['id'] == widget.assignmentId,
          );
          if (matches.isEmpty) {
            return const Center(child: Text('Entrega no encontrada'));
          }
          final item = matches.first;
          final state = '${item['estadoEntrega']}';
          final cash = item['formaPago'] == 'PAGO_EFECTIVO_REPARTIDOR';
          final collected = item['cobroRegistrado'] == true;
          return ListView(
            padding: const EdgeInsets.all(16),
            children: [
              Text(
                'Pedido #${item['pedidoNumero']}',
                style: Theme.of(context).textTheme.headlineSmall,
              ),
              const SizedBox(height: 8),
              Chip(label: Text(_stateLabel(state))),
              Card(
                child: Column(
                  children: [
                    ListTile(
                      leading: const Icon(Icons.person_outline),
                      title: Text('${item['cliente']}'),
                      subtitle: Text('${item['clienteEmail'] ?? ''}'),
                    ),
                    ListTile(
                      leading: const Icon(Icons.phone_outlined),
                      title: Text(
                        '${item['telefono'] ?? 'Teléfono no informado'}',
                      ),
                    ),
                    ListTile(
                      leading: const Icon(Icons.location_on_outlined),
                      title: Text(
                        '${item['direccion'] ?? 'Dirección no informada'}',
                      ),
                      subtitle: Text(
                        'Orden sugerido: ${item['ordenEntrega'] ?? '—'}',
                      ),
                    ),
                  ],
                ),
              ),
              Card(
                child: Column(
                  children: [
                    ListTile(
                      title: const Text('Importe'),
                      trailing: Text(
                        _money(item['importe'], '${item['moneda']}'),
                      ),
                    ),
                    ListTile(
                      title: const Text('Forma de pago'),
                      subtitle: Text(_paymentLabel('${item['formaPago']}')),
                      trailing: Text('${item['estadoPago']}'),
                    ),
                    if (item['observaciones'] != null)
                      ListTile(
                        title: const Text('Observaciones'),
                        subtitle: Text('${item['observaciones']}'),
                      ),
                  ],
                ),
              ),
              FilledButton.tonalIcon(
                onPressed: _busy ? null : () => _navigate(item),
                icon: const Icon(Icons.navigation_outlined),
                label: const Text('Abrir navegación'),
              ),
              if (cash &&
                  !collected &&
                  (state == 'EN_RUTA' || state == 'EN_DESTINO')) ...[
                const SizedBox(height: 10),
                FilledButton.icon(
                  onPressed: _busy ? null : () => _collect(item),
                  icon: const Icon(Icons.payments_outlined),
                  label: Text(
                    'Registrar cobro de ${_money(item['importe'], '${item['moneda']}')}',
                  ),
                ),
              ],
              if (cash && collected)
                const ListTile(
                  leading: Icon(Icons.verified_outlined),
                  title: Text('Cobro registrado'),
                  subtitle: Text('El importe forma parte de tu caja.'),
                ),
              const SizedBox(height: 10),
              ..._stateActions(item),
            ],
          );
        },
      ),
    );
  }

  List<Widget> _stateActions(Map<String, Object?> item) {
    final state = '${item['estadoEntrega']}';
    return switch (state) {
      'ASIGNADO' => [
        FilledButton(
          onPressed: _busy ? null : () => _change('ACEPTADO'),
          child: const Text('Aceptar entrega'),
        ),
      ],
      'ACEPTADO' => [
        FilledButton(
          onPressed: _busy ? null : () => _change('EN_RUTA'),
          child: const Text('Iniciar entrega'),
        ),
      ],
      'EN_RUTA' => [
        FilledButton(
          onPressed: _busy ? null : () => _change('EN_DESTINO'),
          child: const Text('Llegué al destino'),
        ),
        const SizedBox(height: 8),
        OutlinedButton(
          onPressed: _busy ? null : _markUnsuccessful,
          child: const Text('No se pudo entregar'),
        ),
      ],
      'EN_DESTINO' => [
        FilledButton(
          onPressed: _busy
              ? null
              : () => _change('ENTREGADO', result: 'Entrega completada'),
          child: const Text('Finalizar entrega'),
        ),
        const SizedBox(height: 8),
        OutlinedButton(
          onPressed: _busy ? null : _markUnsuccessful,
          child: const Text('No se pudo entregar'),
        ),
      ],
      _ => const [
        ListTile(
          leading: Icon(Icons.history_toggle_off_outlined),
          title: Text('Esta entrega ya finalizó'),
        ),
      ],
    };
  }

  Future<void> _change(String state, {String? result}) async {
    await _run(() async {
      await ref
          .read(courierRepositoryProvider)
          .changeState(widget.assignmentId, state, result: result);
    });
  }

  Future<void> _collect(Map<String, Object?> item) async {
    final confirm = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Confirmar cobro'),
        content: Text(
          'Confirmá que recibiste ${_money(item['importe'], '${item['moneda']}')} en efectivo. Esta operación no puede duplicarse.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Cancelar'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('Confirmar'),
          ),
        ],
      ),
    );
    if (confirm != true) return;
    await _run(
      () => ref.read(courierRepositoryProvider).collect(widget.assignmentId),
    );
  }

  Future<void> _markUnsuccessful() async {
    final controller = TextEditingController();
    final reason = await showDialog<String>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Entrega no realizada'),
        content: TextField(
          controller: controller,
          maxLength: 1000,
          maxLines: 3,
          autofocus: true,
          decoration: const InputDecoration(labelText: 'Motivo obligatorio'),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('Cancelar'),
          ),
          FilledButton(
            onPressed: () {
              final value = controller.text.trim();
              if (value.isNotEmpty) Navigator.pop(context, value);
            },
            child: const Text('Guardar'),
          ),
        ],
      ),
    );
    controller.dispose();
    if (reason != null) await _change('NO_ENTREGADO', result: reason);
  }

  Future<void> _run(Future<void> Function() action) async {
    setState(() => _busy = true);
    try {
      await action();
      ref.invalidate(courierDeliveriesProvider);
      ref.invalidate(courierClientsProvider);
      ref.invalidate(courierRouteProvider);
      ref.invalidate(courierDashboardProvider);
      ref.invalidate(courierCashMovementsProvider);
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(const SnackBar(content: Text('Operación registrada')));
      }
    } on AppFailure catch (failure) {
      if (mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text(failure.message)));
      }
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _navigate(Map<String, Object?> item) async {
    final lat = item['latitud'];
    final lng = item['longitud'];
    final Uri uri;
    if (lat != null && lng != null) {
      uri = Uri.https('www.google.com', '/maps/dir/', {
        'api': '1',
        'destination': '$lat,$lng',
      });
    } else {
      uri = Uri.https('www.google.com', '/maps/search/', {
        'api': '1',
        'query': '${item['direccion'] ?? ''}',
      });
    }
    final opened = await launchUrl(uri, mode: LaunchMode.externalApplication);
    if (!opened && mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('No hay una aplicación de mapas disponible'),
        ),
      );
    }
  }
}

const _stateOrder = [
  'ASIGNADO',
  'ACEPTADO',
  'EN_RUTA',
  'EN_DESTINO',
  'ENTREGADO',
  'NO_ENTREGADO',
  'CANCELADO',
];

String _stateLabel(String value) => switch (value) {
  'ASIGNADO' => 'Asignado',
  'ACEPTADO' => 'Aceptado',
  'EN_RUTA' => 'En ruta',
  'EN_DESTINO' => 'En destino',
  'ENTREGADO' => 'Entregado',
  'NO_ENTREGADO' => 'No entregado',
  'CANCELADO' => 'Cancelado',
  _ => value,
};

String _paymentLabel(String value) => switch (value) {
  'PAGO_EFECTIVO_REPARTIDOR' => 'Efectivo al repartidor',
  'TRANSFERENCIA_BANCARIA' => 'Transferencia bancaria',
  'PAGO_EN_LOCAL' => 'Pago en el local',
  _ => value,
};

String _money(Object? raw, String currency) {
  final value = raw is num ? raw.toDouble() : double.tryParse('$raw') ?? 0;
  return NumberFormat.currency(
    locale: 'es_PY',
    symbol: currency == 'PYG' ? '₲' : currency,
    decimalDigits: currency == 'PYG' ? 0 : 2,
  ).format(value);
}

Widget _empty(String message) => ListView(
  children: [
    const SizedBox(height: 180),
    Center(child: Text(message)),
  ],
);

Widget _retry(String message, VoidCallback retry) => ListView(
  children: [
    const SizedBox(height: 160),
    Center(child: Text(message)),
    Center(
      child: FilledButton(onPressed: retry, child: const Text('Reintentar')),
    ),
  ],
);
