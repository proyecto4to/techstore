import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/di/providers.dart';
import '../../../core/errors/app_failure.dart';

class CashActionsPage extends ConsumerStatefulWidget {
  const CashActionsPage({super.key});

  @override
  ConsumerState<CashActionsPage> createState() => _CashActionsPageState();
}

class _CashActionsPageState extends ConsumerState<CashActionsPage> {
  bool _busy = false;

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('Operar caja')),
    body: ListView(
      padding: const EdgeInsets.all(16),
      children: [
        _ActionCard(
          icon: Icons.lock_open_outlined,
          title: 'Abrir caja',
          subtitle: 'Registra el saldo inicial para comenzar a operar.',
          onTap: _busy ? null : _open,
        ),
        _ActionCard(
          icon: Icons.swap_vert,
          title: 'Registrar movimiento',
          subtitle: 'Ingreso o egreso manual con medio y referencia.',
          onTap: _busy ? null : _movement,
        ),
        _ActionCard(
          icon: Icons.lock_outline,
          title: 'Cerrar caja',
          subtitle: 'Compara el efectivo contado y genera el cierre.',
          onTap: _busy ? null : _close,
        ),
        if (_busy)
          const Padding(
            padding: EdgeInsets.all(24),
            child: Center(child: CircularProgressIndicator()),
          ),
      ],
    ),
  );

  Future<void> _open() async {
    final values = await _showForm(
      title: 'Abrir caja',
      fields: const [
        ('saldoInicial', 'Saldo inicial'),
        ('notas', 'Notas (opcional)'),
      ],
    );
    if (values == null) return;
    await _run('/api/v1/caja/apertura', {
      'saldoInicial': _decimal(values['saldoInicial']),
      'notas': values['notas'],
    });
  }

  Future<void> _movement() async {
    final values = await _showForm(
      title: 'Movimiento de caja',
      fields: const [
        ('tipo', 'Tipo: Ingreso o Egreso'),
        ('monto', 'Monto'),
        ('concepto', 'Concepto'),
        ('referencia', 'Referencia (opcional)'),
        ('medio', 'Medio: Efectivo, Tarjeta, Transferencia o Cheque'),
      ],
    );
    if (values == null) return;
    await _run('/api/v1/caja/movimientos', {
      'tipo': values['tipo'],
      'monto': _decimal(values['monto']),
      'concepto': values['concepto'],
      'referencia': values['referencia'],
      'medio': values['medio']?.isEmpty == true ? null : values['medio'],
    });
  }

  Future<void> _close() async {
    final preview = await ref
        .read(apiClientProvider)
        .get<Map<String, Object?>>('/api/v1/caja/cierres/preview');
    if (!mounted) return;
    final values = await _showForm(
      title:
          'Cerrar caja · esperado ${preview.data?['efectivoEsperado'] ?? '-'}',
      fields: const [
        ('efectivoContado', 'Efectivo contado'),
        ('notas', 'Notas (opcional)'),
      ],
    );
    if (values == null) return;
    await _run('/api/v1/caja/cierres', {
      'efectivoContado': _decimal(values['efectivoContado']),
      'notas': values['notas'],
    });
  }

  Future<Map<String, String>?> _showForm({
    required String title,
    required List<(String, String)> fields,
  }) => showDialog<Map<String, String>>(
    context: context,
    builder: (context) => _CashFormDialog(title: title, fields: fields),
  );

  Future<void> _run(String path, Map<String, Object?> data) async {
    setState(() => _busy = true);
    try {
      await ref.read(apiClientProvider).post<void>(path, data: data);
      _message('Operación registrada');
    } on AppFailure catch (failure) {
      _message(failure.message);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  double _decimal(String? value) =>
      double.parse((value ?? '').replaceAll(',', '.'));

  void _message(String value) {
    if (mounted) {
      ScaffoldMessenger.of(context)
          .showSnackBar(SnackBar(content: Text(value)));
    }
  }
}

class _CashFormDialog extends StatefulWidget {
  const _CashFormDialog({required this.title, required this.fields});
  final String title;
  final List<(String, String)> fields;

  @override
  State<_CashFormDialog> createState() => _CashFormDialogState();
}

class _CashFormDialogState extends State<_CashFormDialog> {
  final _form = GlobalKey<FormState>();
  final _values = <String, String>{};

  @override
  Widget build(BuildContext context) => AlertDialog(
    title: Text(widget.title),
    content: Form(
      key: _form,
      child: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            for (final field in widget.fields)
              Padding(
                padding: const EdgeInsets.only(bottom: 10),
                child: TextFormField(
                  decoration: InputDecoration(labelText: field.$2),
                  validator: field.$2.contains('opcional')
                      ? null
                      : (value) => value == null || value.trim().isEmpty
                            ? 'Requerido'
                            : null,
                  onSaved: (value) => _values[field.$1] = value?.trim() ?? '',
                ),
              ),
          ],
        ),
      ),
    ),
    actions: [
      TextButton(
        onPressed: () => Navigator.pop(context),
        child: const Text('Cancelar'),
      ),
      FilledButton(
        onPressed: () {
          if (!_form.currentState!.validate()) return;
          _form.currentState!.save();
          Navigator.pop(context, _values);
        },
        child: const Text('Confirmar'),
      ),
    ],
  );
}

class _ActionCard extends StatelessWidget {
  const _ActionCard({
    required this.icon,
    required this.title,
    required this.subtitle,
    this.onTap,
  });
  final IconData icon;
  final String title;
  final String subtitle;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) => Card(
    child: ListTile(
      leading: Icon(icon),
      title: Text(title),
      subtitle: Text(subtitle),
      trailing: const Icon(Icons.chevron_right),
      onTap: onTap,
    ),
  );
}
