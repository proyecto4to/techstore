import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/di/providers.dart';
import '../../../core/errors/app_failure.dart';
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
                    decoration: const InputDecoration(
                      labelText: 'Agregar producto',
                      prefixIcon: Icon(Icons.search),
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
                          child: TextField(
                            controller: _lines[index].cost,
                            keyboardType: const TextInputType.numberWithOptions(
                              decimal: true,
                            ),
                            decoration: const InputDecoration(
                              labelText: 'Costo unitario',
                            ),
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
      final cost = double.tryParse(line.cost.text.replaceAll(',', '.'));
      if (quantity == null || quantity < 1 || cost == null || cost <= 0) {
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
      await ref
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
      _message('Compra registrada');
      setState(() {});
    } on AppFailure catch (failure) {
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
