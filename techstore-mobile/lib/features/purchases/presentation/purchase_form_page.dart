import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/di/providers.dart';
import '../../../core/errors/app_failure.dart';
import '../../../core/errors/user_message.dart';
import '../../../core/format/money_input.dart';
import '../../../core/native/haptics_service.dart';
import '../../../core/widgets/image_origin_sheet.dart';
import '../../../core/widgets/money_field.dart';
import '../../../core/widgets/voice_dictation_button.dart';
import '../../catalog/application/catalog_providers.dart';
import '../../catalog/domain/product.dart';

class PurchaseFormPage extends ConsumerStatefulWidget {
  const PurchaseFormPage({super.key});

  @override
  ConsumerState<PurchaseFormPage> createState() => _PurchaseFormPageState();
}

class _PurchaseFormPageState extends ConsumerState<PurchaseFormPage> {
  final _lines = <_PurchaseLine>[];
  final _notes = TextEditingController();
  List<Map<String, Object?>> _suppliers = const [];
  int? _supplierId;
  bool _busy = false;

  @override
  void initState() {
    super.initState();
    _loadSuppliers();
  }

  @override
  void dispose() {
    for (final line in _lines) {
      line.dispose();
    }
    _notes.dispose();
    super.dispose();
  }

  Future<void> _loadSuppliers() async {
    try {
      final response = await ref
          .read(apiClientProvider)
          .get<Object?>('/api/v1/proveedores/activos');
      final raw = response.data;
      final list = raw is List
          ? raw
          : raw is Map && raw['content'] is List
          ? raw['content']! as List
          : const [];
      if (mounted) {
        setState(() {
          _suppliers = list
              .map((item) => Map<String, Object?>.from(item as Map))
              .toList(growable: false);
        });
      }
    } on AppFailure catch (failure) {
      _message(failure.message);
    }
  }

  @override
  Widget build(BuildContext context) {
    final products = ref.watch(catalogProvider((search: '', brandId: null)));
    return Scaffold(
      appBar: AppBar(title: const Text('Nueva compra')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          DropdownButtonFormField<int>(
            initialValue: _supplierId,
            decoration: const InputDecoration(labelText: 'Proveedor'),
            items: [
              for (final supplier in _suppliers)
                DropdownMenuItem(
                  value: (supplier['id'] as num).toInt(),
                  child: Text(
                    supplier['razonSocial']?.toString() ?? 'Proveedor',
                  ),
                ),
            ],
            onChanged: (value) => setState(() => _supplierId = value),
          ),
          const SizedBox(height: 16),
          products.when(
            loading: () => const LinearProgressIndicator(),
            error: (error, _) => const Text('No se pudo cargar el catálogo'),
            data: (values) => Autocomplete<Product>(
              displayStringForOption: (product) => product.name,
              optionsBuilder: (value) {
                final term = value.text.toLowerCase();
                return values.where(
                  (product) => product.name.toLowerCase().contains(term),
                );
              },
              onSelected: (product) =>
                  setState(() => _lines.add(_PurchaseLine(product: product))),
              fieldViewBuilder: (context, controller, focusNode, onSubmitted) =>
                  TextField(
                    controller: controller,
                    focusNode: focusNode,
                    decoration: InputDecoration(
                      labelText: 'Agregar producto',
                      prefixIcon: const Icon(Icons.search),
                      suffixIcon: VoiceDictationButton(
                        controller: controller,
                        tooltip: 'Buscar producto por voz',
                      ),
                    ),
                  ),
            ),
          ),
          const SizedBox(height: 12),
          for (var index = 0; index < _lines.length; index++)
            Card(
              child: Padding(
                padding: const EdgeInsets.all(12),
                child: Column(
                  children: [
                    ListTile(
                      title: Text(_lines[index].product.name),
                      trailing: IconButton(
                        tooltip: 'Eliminar línea',
                        onPressed: () =>
                            setState(() => _lines.removeAt(index).dispose()),
                        icon: const Icon(Icons.delete_outline),
                      ),
                    ),
                    Row(
                      children: [
                        Expanded(
                          child: TextField(
                            controller: _lines[index].quantity,
                            keyboardType: TextInputType.number,
                            decoration: const InputDecoration(
                              labelText: 'Cantidad',
                            ),
                          ),
                        ),
                        const SizedBox(width: 12),
                        Expanded(
                          child: MoneyField(
                            controller: _lines[index].cost,
                            label: 'Costo unitario',
                            decimales: 2,
                          ),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
            ),
          TextField(
            controller: _notes,
            maxLines: 3,
            decoration: const InputDecoration(labelText: 'Notas (opcional)'),
          ),
          const SizedBox(height: 16),
          OutlinedButton.icon(
            onPressed: _busy || _lines.isEmpty ? null : _compare,
            icon: const Icon(Icons.compare_arrows),
            label: const Text('Comparar proveedores'),
          ),
          FilledButton.icon(
            onPressed: _busy || _lines.isEmpty || _supplierId == null
                ? null
                : _submit,
            icon: const Icon(Icons.shopping_bag_outlined),
            label: const Text('Registrar compra'),
          ),
        ],
      ),
    );
  }

  List<Map<String, Object?>> _comparisonItems() => [
    for (final line in _lines)
      {
        'productoId': line.product.id,
        'cantidad': int.tryParse(line.quantity.text) ?? 0,
      },
  ];

  Future<void> _compare() async {
    setState(() => _busy = true);
    try {
      final response = await ref
          .read(apiClientProvider)
          .post<Object?>(
            '/api/v1/compras/comparar',
            data: {'items': _comparisonItems()},
          );
      if (!mounted) return;
      await showDialog<void>(
        context: context,
        builder: (context) => AlertDialog(
          title: const Text('Comparación'),
          content: SingleChildScrollView(
            child: SelectableText(
              const JsonEncoder.withIndent('  ').convert(response.data),
            ),
          ),
          actions: [
            FilledButton(
              onPressed: () => Navigator.pop(context),
              child: const Text('Cerrar'),
            ),
          ],
        ),
      );
    } on AppFailure catch (failure) {
      _message(failure.message);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _submit() async {
    final items = <Map<String, Object?>>[];
    for (final line in _lines) {
      final quantity = int.tryParse(line.quantity.text);
      // El campo muestra el monto agrupado; lo que viaja es el número.
      final cost = parsearMonto(line.cost.text, decimales: 2);
      if (quantity == null || quantity < 1 || cost == null || cost <= 0) {
        await ref.read(hapticsServiceProvider).play(HapticCue.error);
        _message('Revisá cantidad y costo de cada producto');
        return;
      }
      items.add({
        'productoId': line.product.id,
        'cantidad': quantity,
        'costoUnitario': cost,
      });
    }
    setState(() => _busy = true);
    try {
      final response = await ref
          .read(apiClientProvider)
          .post<Object?>(
            '/api/v1/compras',
            data: {
              'proveedorId': _supplierId,
              'items': items,
              if (_notes.text.trim().isNotEmpty) 'notas': _notes.text.trim(),
            },
          );
      for (final line in _lines) {
        line.dispose();
      }
      _lines.clear();
      final data = response.data;
      final purchaseId = data is Map ? (data['id'] as num?)?.toInt() : null;
      await ref.read(hapticsServiceProvider).play(HapticCue.success);
      _message('Compra registrada');
      setState(() {});
      if (purchaseId != null) await _offerReceipt(purchaseId);
    } on AppFailure catch (failure) {
      await ref.read(hapticsServiceProvider).play(HapticCue.error);
      _message(failure.message);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  /// Propone fotografiar el comprobante en el momento, mientras el papel sigue
  /// en la mano, en vez de obligar a buscar la compra después en el panel.
  Future<void> _offerReceipt(int purchaseId) async {
    if (!mounted) return;
    final wanted = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Adjuntar comprobante'),
        content: const Text(
          '¿Querés fotografiar ahora el comprobante de esta compra?',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Más tarde'),
          ),
          FilledButton.icon(
            onPressed: () => Navigator.pop(context, true),
            icon: const Icon(Icons.document_scanner_outlined),
            label: const Text('Fotografiar'),
          ),
        ],
      ),
    );
    if (wanted != true || !mounted) return;
    final origin = await pickImageOrigin(
      context,
      cameraLabel: 'Fotografiar comprobante',
      cameraIcon: Icons.document_scanner_outlined,
    );
    if (origin == null || !mounted) return;
    try {
      final uploaded = await ref
          .read(imageUploadServiceProvider)
          .pickAndUploadPurchaseReceipt(purchaseId, origin);
      if (!uploaded) return;
      await ref.read(hapticsServiceProvider).play(HapticCue.success);
      _message('Comprobante adjuntado');
    } catch (error) {
      await ref.read(hapticsServiceProvider).play(HapticCue.error);
      _message(
        userMessageFor(
          error,
          fallback:
              'No pudimos adjuntar el comprobante. '
              'Probá de nuevo en un momento.',
        ),
      );
    }
  }

  void _message(String value) {
    if (mounted) {
      ScaffoldMessenger.of(context)
          .showSnackBar(SnackBar(content: Text(value)));
    }
  }
}

class _PurchaseLine {
  _PurchaseLine({required this.product});
  final Product product;
  final quantity = TextEditingController(text: '1');
  final cost = TextEditingController();

  void dispose() {
    quantity.dispose();
    cost.dispose();
  }
}
