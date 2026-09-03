import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';

import '../../../core/errors/app_failure.dart';
import '../application/courier_providers.dart';

class CourierCashPage extends ConsumerStatefulWidget {
  const CourierCashPage({super.key});

  @override
  ConsumerState<CourierCashPage> createState() => _CourierCashPageState();
}

class _CourierCashPageState extends ConsumerState<CourierCashPage> {
  bool _busy = false;

  @override
  Widget build(BuildContext context) {
    final dashboard = ref.watch(courierDashboardProvider);
    final movements = ref.watch(courierCashMovementsProvider);
    final transfers = ref.watch(courierTransfersProvider);
    return Scaffold(
      appBar: AppBar(title: const Text('Mi caja')),
      body: RefreshIndicator(
        onRefresh: _refresh,
        child: ListView(
          padding: const EdgeInsets.all(16),
          children: [
            dashboard.when(
              loading: () => const LinearProgressIndicator(),
              error: (error, _) => const Text('No se pudo consultar el saldo'),
              data: (data) => Card(
                child: Padding(
                  padding: const EdgeInsets.all(18),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      const Text('Saldo actual derivado de movimientos'),
                      const SizedBox(height: 6),
                      Text(
                        _money(data['saldoActual'], '${data['moneda']}'),
                        style: Theme.of(context).textTheme.headlineMedium,
                      ),
                      const SizedBox(height: 12),
                      FilledButton.icon(
                        onPressed: _busy || _number(data['saldoActual']) <= 0
                            ? null
                            : () => _requestTransfer(data),
                        icon: const Icon(Icons.move_to_inbox_outlined),
                        label: const Text('VACIAR CAJA'),
                      ),
                      const SizedBox(height: 8),
                      const Text(
                        'Se enviará una solicitud por el saldo completo. El dinero seguirá en tu caja hasta que el cajero acepte.',
                        style: TextStyle(fontSize: 12),
                      ),
                    ],
                  ),
                ),
              ),
            ),
            const SizedBox(height: 16),
            Text('Solicitudes', style: Theme.of(context).textTheme.titleLarge),
            transfers.when(
              loading: () => const LinearProgressIndicator(),
              error: (error, _) => const ListTile(
                title: Text('No se pudieron cargar las solicitudes'),
              ),
              data: (items) => items.isEmpty
                  ? const ListTile(
                      title: Text('Todavía no realizaste transferencias'),
                    )
                  : Column(
                      children: [
                        for (final item in items) _TransferCard(item: item),
                      ],
                    ),
            ),
            const SizedBox(height: 16),
            Text('Movimientos', style: Theme.of(context).textTheme.titleLarge),
            movements.when(
              loading: () => const LinearProgressIndicator(),
              error: (error, _) => const ListTile(
                title: Text('No se pudieron cargar los movimientos'),
              ),
              data: (items) => items.isEmpty
                  ? const ListTile(title: Text('Sin movimientos registrados'))
                  : Column(
                      children: [
                        for (final item in items) _MovementTile(item: item),
                      ],
                    ),
            ),
          ],
        ),
      ),
    );
  }

  Future<void> _requestTransfer(Map<String, Object?> dashboard) async {
    final cashiers = await ref.read(courierCashiersProvider.future);
    if (!mounted) return;
    if (cashiers.isEmpty) {
      _message('No hay cajeros habilitados');
      return;
    }
    final note = TextEditingController();
    int? selected = cashiers.first['id'] as int?;
    final result = await showDialog<(int, String?)>(
      context: context,
      builder: (context) => StatefulBuilder(
        builder: (context, setDialogState) => AlertDialog(
          title: const Text('Solicitar vaciado de caja'),
          content: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Text(
                  'Monto completo: ${_money(dashboard['saldoActual'], '${dashboard['moneda']}')}',
                  style: Theme.of(context).textTheme.titleMedium,
                ),
                const SizedBox(height: 14),
                DropdownButtonFormField<int>(
                  initialValue: selected,
                  decoration: const InputDecoration(
                    labelText: 'Cajero habilitado',
                  ),
                  items: [
                    for (final cashier in cashiers)
                      DropdownMenuItem(
                        value: cashier['id'] as int,
                        child: Text(
                          '${cashier['nombre']} ${cashier['apellido']}',
                        ),
                      ),
                  ],
                  onChanged: (value) => setDialogState(() => selected = value),
                ),
                const SizedBox(height: 12),
                TextField(
                  controller: note,
                  maxLength: 1000,
                  maxLines: 3,
                  decoration: const InputDecoration(
                    labelText: 'Observación opcional',
                  ),
                ),
              ],
            ),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(context),
              child: const Text('Cancelar'),
            ),
            FilledButton(
              onPressed: selected == null
                  ? null
                  : () => Navigator.pop(context, (selected!, note.text.trim())),
              child: const Text('Confirmar solicitud'),
            ),
          ],
        ),
      ),
    );
    note.dispose();
    if (result == null) return;
    setState(() => _busy = true);
    try {
      await ref
          .read(courierRepositoryProvider)
          .requestCashTransfer(result.$1, result.$2);
      await _refresh();
      _message(
        'Solicitud enviada. El saldo sigue en tu caja hasta la aceptación.',
      );
    } on AppFailure catch (failure) {
      _message(failure.message);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _refresh() async {
    ref.invalidate(courierDashboardProvider);
    ref.invalidate(courierCashMovementsProvider);
    ref.invalidate(courierTransfersProvider);
    await Future.wait([
      ref.read(courierDashboardProvider.future),
      ref.read(courierCashMovementsProvider.future),
      ref.read(courierTransfersProvider.future),
    ]);
  }

  void _message(String value) {
    if (mounted) {
      ScaffoldMessenger.of(context)
          .showSnackBar(SnackBar(content: Text(value)));
    }
  }
}

class _TransferCard extends StatelessWidget {
  const _TransferCard({required this.item});
  final Map<String, Object?> item;

  @override
  Widget build(BuildContext context) {
    final state = '${item['estado']}';
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            ListTile(
              contentPadding: EdgeInsets.zero,
              leading: Icon(switch (state) {
                'ACEPTADO' => Icons.check_circle_outline,
                'RECHAZADO' => Icons.cancel_outlined,
                _ => Icons.schedule_outlined,
              }),
              title: Text(_money(item['monto'], '${item['moneda']}')),
              subtitle: Text(
                '${_transferLabel(state)} · ${_date(item['fechaSolicitud'])}\nCajero: ${item['cajero']}',
              ),
              isThreeLine: true,
            ),
            if (item['observacion'] != null)
              Text('Observación: ${item['observacion']}'),
            if (state == 'RECHAZADO')
              Container(
                margin: const EdgeInsets.only(top: 8),
                padding: const EdgeInsets.all(10),
                decoration: BoxDecoration(
                  color: Theme.of(context).colorScheme.errorContainer,
                  borderRadius: BorderRadius.circular(8),
                ),
                child: Text('Motivo del rechazo: ${item['motivoRechazo']}'),
              ),
          ],
        ),
      ),
    );
  }
}

class _MovementTile extends StatelessWidget {
  const _MovementTile({required this.item});
  final Map<String, Object?> item;

  @override
  Widget build(BuildContext context) {
    final credit = item['naturaleza'] == 'CREDITO';
    return ListTile(
      leading: CircleAvatar(
        child: Icon(credit ? Icons.south_west : Icons.north_east),
      ),
      title: Text('${item['tipo']}'.replaceAll('_', ' ')),
      subtitle: Text('${_date(item['fecha'])}\n${item['observacion'] ?? ''}'),
      isThreeLine: item['observacion'] != null,
      trailing: Text(
        '${credit ? '+' : '−'}${_money(item['monto'], '${item['moneda']}')}',
        style: TextStyle(color: credit ? Colors.green : Colors.red),
      ),
    );
  }
}

double _number(Object? raw) =>
    raw is num ? raw.toDouble() : double.tryParse('$raw') ?? 0;

String _money(Object? raw, String currency) => NumberFormat.currency(
  locale: 'es_PY',
  symbol: currency == 'PYG' ? '₲' : currency,
  decimalDigits: currency == 'PYG' ? 0 : 2,
).format(_number(raw));

String _date(Object? raw) {
  final value = DateTime.tryParse('$raw');
  return value == null
      ? '—'
      : DateFormat.yMd('es_PY').add_Hm().format(value.toLocal());
}

String _transferLabel(String value) => switch (value) {
  'PENDIENTE_ACEPTACION' => 'Pendiente de aceptación',
  'ACEPTADO' => 'Aceptada',
  'RECHAZADO' => 'Rechazada',
  'CANCELADO' => 'Cancelada',
  _ => value,
};
