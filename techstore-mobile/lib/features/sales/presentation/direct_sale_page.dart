import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/di/providers.dart';
import '../../../core/errors/app_failure.dart';
import '../../../core/native/haptics_service.dart';
import '../../../core/widgets/voice_dictation_button.dart';
import '../../catalog/application/catalog_providers.dart';
import '../../catalog/domain/product.dart';

class DirectSalePage extends ConsumerStatefulWidget {
  const DirectSalePage({super.key});

  @override
  ConsumerState<DirectSalePage> createState() => _DirectSalePageState();
}

class _DirectSalePageState extends ConsumerState<DirectSalePage> {
  final _items = <int, (Product, int)>{};
  final _payments = <_PaymentLine>[_PaymentLine()];
  bool _busy = false;

  double get _total =>
      _items.values.fold(0, (sum, line) => sum + line.$1.price * line.$2);

  @override
  void dispose() {
    for (final line in _payments) {
      line.dispose();
    }
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final products = ref.watch(catalogProvider((search: '', brandId: null)));
    return Scaffold(
      appBar: AppBar(title: const Text('Venta directa')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          products.when(
            loading: () => const LinearProgressIndicator(),
            error: (error, _) => const Text('No se pudo cargar el catálogo'),
            data: (values) => Autocomplete<Product>(
              displayStringForOption: (product) => product.name,
              optionsBuilder: (value) {
                final term = value.text.toLowerCase();
                return values.where(
                  (product) =>
                      product.available &&
                      product.name.toLowerCase().contains(term),
                );
              },
              onSelected: (product) {
                setState(() {
                  final current = _items[product.id];
                  _items[product.id] = (product, (current?.$2 ?? 0) + 1);
                });
                _cue(HapticCue.success);
              },
              fieldViewBuilder: (context, controller, focusNode, onSubmitted) =>
                  TextField(
                    controller: controller,
                    focusNode: focusNode,
                    decoration: InputDecoration(
                      labelText: 'Buscar y agregar producto',
                      prefixIcon: const Icon(Icons.search),
                      suffixIcon: VoiceDictationButton(
                        controller: controller,
                        tooltip: 'Buscar producto por voz',
                      ),
                    ),
                  ),
            ),
          ),
          const SizedBox(height: 16),
          for (final entry in _items.entries)
            ListTile(
              title: Text(entry.value.$1.name),
              subtitle: Text(
                '${entry.value.$1.price} ${entry.value.$1.currency}',
              ),
              trailing: Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  IconButton(
                    tooltip: 'Disminuir cantidad',
                    onPressed: () =>
                        _setQuantity(entry.key, entry.value.$2 - 1),
                    icon: const Icon(Icons.remove_circle_outline),
                  ),
                  Text('${entry.value.$2}'),
                  IconButton(
                    tooltip: 'Aumentar cantidad',
                    onPressed: () =>
                        _setQuantity(entry.key, entry.value.$2 + 1),
                    icon: const Icon(Icons.add_circle_outline),
                  ),
                ],
              ),
            ),
          const Divider(height: 32),
          Text(
            'Pagos · Total $_total PYG',
            style: Theme.of(context).textTheme.titleLarge,
          ),
          for (var index = 0; index < _payments.length; index++)
            _PaymentEditor(
              line: _payments[index],
              canDelete: _payments.length > 1,
              onDelete: () => setState(() {
                _payments.removeAt(index).dispose();
              }),
            ),
          TextButton.icon(
            onPressed: () => setState(() => _payments.add(_PaymentLine())),
            icon: const Icon(Icons.add),
            label: const Text('Agregar medio de pago'),
          ),
          const SizedBox(height: 16),
          FilledButton.icon(
            onPressed: _busy || _items.isEmpty ? null : _submit,
            icon: const Icon(Icons.point_of_sale),
            label: const Text('Confirmar venta y facturar'),
          ),
        ],
      ),
    );
  }

  void _setQuantity(int id, int quantity) {
    setState(() {
      if (quantity <= 0) {
        _items.remove(id);
      } else {
        final product = _items[id]!.$1;
        _items[id] = (product, quantity);
      }
    });
    _cue(HapticCue.tap);
  }

  void _cue(HapticCue cue) {
    unawaited(ref.read(hapticsServiceProvider).play(cue));
  }

  Future<void> _submit() async {
    final payments = <Map<String, Object?>>[];
    for (final line in _payments) {
      final amount = double.tryParse(line.amount.text.replaceAll(',', '.'));
      if (amount == null || amount <= 0) {
        _cue(HapticCue.error);
        _message('Ingresá montos válidos para todos los pagos');
        return;
      }
      payments.add({
        'tipoPago': line.type,
        'monto': amount,
        if (line.reference.text.trim().isNotEmpty)
          'numeroComprobante': line.reference.text.trim(),
      });
    }
    final confirmed = await ref
        .read(biometricServiceProvider)
        .confirmOperation(
          reason: 'Confirmá la venta por ${_total.toStringAsFixed(0)} PYG',
        );
    if (!confirmed) {
      _cue(HapticCue.error);
      _message('Venta cancelada: no se validó la identidad');
      return;
    }
    setState(() => _busy = true);
    try {
      final response = await ref
          .read(apiClientProvider)
          .post<Map<String, Object?>>(
            '/api/v1/ventas',
            data: {
              'items': [
                for (final line in _items.values)
                  {
                    'productoId': line.$1.id,
                    'cantidad': line.$2,
                    'descuento': 0,
                  },
              ],
              'lineas': payments,
            },
          );
      _items.clear();
      _cue(HapticCue.success);
      _message('Venta #${response.data?['numero']} registrada');
      setState(() {});
    } on AppFailure catch (failure) {
      _cue(HapticCue.error);
      _message(failure.message);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  void _message(String value) {
    if (mounted) {
      ScaffoldMessenger.of(context)
          .showSnackBar(SnackBar(content: Text(value)));
    }
  }
}

class _PaymentLine {
  String type = 'Efectivo';
  final amount = TextEditingController();
  final reference = TextEditingController();

  void dispose() {
    amount.dispose();
    reference.dispose();
  }
}

class _PaymentEditor extends StatefulWidget {
  const _PaymentEditor({
    required this.line,
    required this.canDelete,
    required this.onDelete,
  });
  final _PaymentLine line;
  final bool canDelete;
  final VoidCallback onDelete;

  @override
  State<_PaymentEditor> createState() => _PaymentEditorState();
}

class _PaymentEditorState extends State<_PaymentEditor> {
  @override
  Widget build(BuildContext context) => Card(
    child: Padding(
      padding: const EdgeInsets.all(12),
      child: Column(
        children: [
          DropdownButtonFormField<String>(
            initialValue: widget.line.type,
            decoration: const InputDecoration(labelText: 'Medio'),
            items: const [
              DropdownMenuItem(value: 'Efectivo', child: Text('Efectivo')),
              DropdownMenuItem(value: 'Tarjeta', child: Text('Tarjeta')),
              DropdownMenuItem(
                value: 'Transferencia',
                child: Text('Transferencia'),
              ),
              DropdownMenuItem(value: 'Cheque', child: Text('Cheque')),
            ],
            onChanged: (value) => setState(() => widget.line.type = value!),
          ),
          TextField(
            controller: widget.line.amount,
            keyboardType: const TextInputType.numberWithOptions(decimal: true),
            decoration: const InputDecoration(labelText: 'Monto'),
          ),
          TextField(
            controller: widget.line.reference,
            decoration: const InputDecoration(
              labelText: 'Comprobante (opcional)',
            ),
          ),
          if (widget.canDelete)
            Align(
              alignment: Alignment.centerRight,
              child: IconButton(
                tooltip: 'Eliminar línea',
                onPressed: widget.onDelete,
                icon: const Icon(Icons.delete_outline),
              ),
            ),
        ],
      ),
    ),
  );
}
