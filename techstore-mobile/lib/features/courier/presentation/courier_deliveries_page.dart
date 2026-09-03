import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';
import 'package:url_launcher/url_launcher.dart';

import '../../../core/errors/app_failure.dart';
import '../../../core/format/status_labels.dart';
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
                      '${deliveryStatusLabel(state).label} · ${groups[state]!.length}',
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
                              deliveryStatusLabel(item['estadoEntrega'] as String?).label,
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
        '${paymentMethodLabel(item['formaPago'] as String?)} · '
        '${paymentStatusLabel(item['estadoPago'] as String?).label}',
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
              Align(
                alignment: Alignment.centerLeft,
                child: StatusChip(status: deliveryStatusLabel(state)),
              ),
              if (deliveryStatusLabel(state).detail != null)
                Padding(
                  padding: const EdgeInsets.only(top: 6),
                  child: Text(
                    deliveryStatusLabel(state).detail!,
                    style: Theme.of(context).textTheme.bodySmall?.copyWith(
                      color: Theme.of(context).colorScheme.onSurfaceVariant,
                    ),
                  ),
                ),
              const SizedBox(height: 8),
              Card(
                child: Column(
                  children: [
                    ListTile(
                      leading: const Icon(Icons.person_outline),
                      title: Text('${item['cliente']}'),
                      subtitle: Text('${item['clienteEmail'] ?? ''}'),
                    ),
                    // El teléfono se toca y llama. Antes era texto muerto: el
                    // repartidor tenía que memorizarlo y salir a la app de
                    // teléfono a mano, en la calle y con el pedido en brazos.
                    ListTile(
                      leading: const Icon(Icons.phone_outlined),
                      title: Text(
                        '${item['telefono'] ?? 'Teléfono no informado'}',
                      ),
                      subtitle: item['telefono'] == null
                          ? null
                          : const Text('Tocá para llamar'),
                      trailing: item['telefono'] == null
                          ? null
                          : const Icon(Icons.call_outlined),
                      onTap: item['telefono'] == null
                          ? null
                          : () => _call('${item['telefono']}'),
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
                      subtitle: Text(
                        paymentMethodLabel(item['formaPago'] as String?),
                      ),
                      trailing: StatusChip(
                        status: paymentStatusLabel(item['estadoPago'] as String?),
                        compact: true,
                      ),
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

  /// La acción que corresponde según el estado, y solo esa.
  ///
  /// El orden lo impone el servidor (`EstadoReparto`), así que la pantalla no
  /// ofrece saltos: no hay forma de marcar entregado algo que nunca se inició.
  List<Widget> _stateActions(Map<String, Object?> item) {
    final state = '${item['estadoEntrega']}';
    return switch (state) {
      'ASIGNADO' => [
        _MainAction(
          icon: Icons.thumb_up_outlined,
          label: 'Aceptar esta entrega',
          busy: _busy,
          onPressed: () => _change('ACEPTADO'),
        ),
      ],
      'ACEPTADO' => [
        _MainAction(
          icon: Icons.play_arrow_rounded,
          label: 'Iniciar entrega',
          busy: _busy,
          onPressed: () => _change('EN_RUTA'),
        ),
      ],
      'EN_RUTA' => [
        _MainAction(
          icon: Icons.where_to_vote_outlined,
          label: 'Llegué al domicilio',
          busy: _busy,
          onPressed: () => _change('EN_DESTINO'),
        ),
        const SizedBox(height: 10),
        _SecondaryAction(
          label: 'No se pudo entregar',
          busy: _busy,
          onPressed: _markUnsuccessful,
        ),
      ],
      'EN_DESTINO' => [
        _MainAction(
          icon: Icons.check_circle_outline,
          label: 'Marcar como entregado',
          busy: _busy,
          onPressed: () => _change('ENTREGADO', result: 'Entrega completada'),
        ),
        const SizedBox(height: 10),
        _SecondaryAction(
          label: 'No se pudo entregar',
          busy: _busy,
          onPressed: _markUnsuccessful,
        ),
      ],
      _ => const [
        ListTile(
          leading: Icon(Icons.history_toggle_off_outlined),
          title: Text('Esta entrega ya finalizó'),
          subtitle: Text('No queda nada por hacer acá.'),
        ),
      ],
    };
  }

  Future<void> _change(String state, {String? result, String? message}) async {
    await _run(() async {
      await ref
          .read(courierRepositoryProvider)
          .changeState(widget.assignmentId, state, result: result);
    }, message: message ?? _changeMessage(state));
  }

  /// Qué se le dice al repartidor después de cada transición. Un «Operación
  /// registrada» genérico no confirma *qué* quedó registrado, que es
  /// justamente lo que necesita saber antes de guardar el teléfono.
  String _changeMessage(String state) => switch (state) {
    'ACEPTADO' => 'Aceptaste la entrega',
    'EN_RUTA' => 'Entrega iniciada. Buen viaje.',
    'EN_DESTINO' => 'Llegada registrada',
    'ENTREGADO' => 'Entrega completada',
    'NO_ENTREGADO' => 'Registramos que no se pudo entregar',
    _ => 'Listo',
  };

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
      message: 'Cobro registrado. Ya forma parte de tu caja.',
    );
  }

  Future<void> _markUnsuccessful() async {
    final controller = TextEditingController();
    String? error;
    final reason = await showDialog<String>(
      context: context,
      builder: (context) => StatefulBuilder(
        builder: (context, setDialogState) => AlertDialog(
          title: const Text('¿No se pudo entregar?'),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              const Text(
                'Contá qué pasó. Lo va a leer quien atienda al cliente '
                'después, así que conviene ser concreto.',
              ),
              const SizedBox(height: 14),
              TextField(
                controller: controller,
                maxLength: 1000,
                maxLines: 3,
                autofocus: true,
                decoration: InputDecoration(
                  labelText: 'Motivo',
                  hintText: 'Por ejemplo: no había nadie en el domicilio',
                  errorText: error,
                ),
                // El error se va apenas empieza a corregir, en vez de quedar
                // pegado al campo que ya está bien.
                onChanged: error == null
                    ? null
                    : (_) => setDialogState(() => error = null),
              ),
            ],
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(context),
              child: const Text('Volver'),
            ),
            FilledButton(
              // Antes, con el campo vacío este botón no hacía nada y no
              // explicaba por qué: el repartidor quedaba tocándolo sin
              // respuesta. Ahora dice qué falta.
              onPressed: () {
                final value = controller.text.trim();
                if (value.isEmpty) {
                  setDialogState(
                    () => error = 'Escribí el motivo para poder registrarlo',
                  );
                  return;
                }
                Navigator.pop(context, value);
              },
              child: const Text('Registrar el problema'),
            ),
          ],
        ),
      ),
    );
    controller.dispose();
    if (reason != null) {
      await _change(
        'NO_ENTREGADO',
        result: reason,
        message: 'Registramos que no se pudo entregar',
      );
    }
  }

  Future<void> _run(
    Future<void> Function() action, {
    required String message,
  }) async {
    setState(() => _busy = true);
    try {
      await action();
      ref.invalidate(courierDeliveriesProvider);
      ref.invalidate(courierClientsProvider);
      ref.invalidate(courierRouteProvider);
      ref.invalidate(courierDashboardProvider);
      ref.invalidate(courierCashMovementsProvider);
      if (mounted) {
        ScaffoldMessenger.of(context)
          ..hideCurrentSnackBar()
          ..showSnackBar(SnackBar(content: Text(message)));
      }
    } on AppFailure catch (failure) {
      if (mounted) {
        ScaffoldMessenger.of(context)
          ..hideCurrentSnackBar()
          ..showSnackBar(
            SnackBar(
              content: Text(failure.message),
              duration: const Duration(seconds: 5),
            ),
          );
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

  /// Abre el marcador del teléfono con el número del cliente ya cargado.
  Future<void> _call(String phone) async {
    // Se dejan solo los dígitos y el prefijo internacional: los espacios y
    // guiones con que se cargó el número rompen el esquema `tel:`.
    final digits = phone.replaceAll(RegExp(r'[^0-9+]'), '');
    if (digits.isEmpty) return;
    final opened = await launchUrl(Uri(scheme: 'tel', path: digits));
    if (!opened && mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('No pudimos llamar. El número es $phone')),
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

/// La acción principal del estado actual.
///
/// Alta y a todo el ancho porque se toca de parado, en la puerta de una casa,
/// con una mano ocupada. Se bloquea mientras hay una operación en curso para
/// que un segundo toque no repita la transición.
class _MainAction extends StatelessWidget {
  const _MainAction({
    required this.icon,
    required this.label,
    required this.busy,
    required this.onPressed,
  });

  final IconData icon;
  final String label;
  final bool busy;
  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) => SizedBox(
    height: 56,
    width: double.infinity,
    child: FilledButton.icon(
      onPressed: busy ? null : onPressed,
      icon: busy
          ? const SizedBox.square(
              dimension: 20,
              child: CircularProgressIndicator(strokeWidth: 2),
            )
          : Icon(icon, size: 22),
      label: Text(
        busy ? 'Registrando...' : label,
        style: const TextStyle(fontSize: 16),
      ),
    ),
  );
}

/// La salida alternativa: presente, pero visiblemente menos que la principal.
class _SecondaryAction extends StatelessWidget {
  const _SecondaryAction({
    required this.label,
    required this.busy,
    required this.onPressed,
  });

  final String label;
  final bool busy;
  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) => SizedBox(
    height: 50,
    width: double.infinity,
    child: OutlinedButton(
      onPressed: busy ? null : onPressed,
      child: Text(label),
    ),
  );
}
