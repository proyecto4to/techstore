import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/auth/operation_confirm.dart';
import '../../../core/di/providers.dart';
import '../../../core/errors/app_failure.dart';
import '../../../core/format/money.dart';
import '../../../core/format/money_input.dart';
import '../../../core/native/haptics_service.dart';
import '../../../core/widgets/async_states.dart';
import '../../../core/widgets/money_field.dart';
import '../../../core/widgets/quantity_stepper.dart';
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
  final _searchController = TextEditingController();
  Timer? _debounce;

  /// Lo que se le pide al servidor. Se separa del texto que se está tipeando
  /// para no disparar una consulta por tecla.
  String _term = '';
  bool _busy = false;

  double get _total =>
      _items.values.fold(0, (sum, line) => sum + line.$1.price * line.$2);

  /// La moneda de lo que se está vendiendo. Se toma del producto en vez de
  /// asumir guaraníes escritos a mano en el texto.
  String get _currency =>
      _items.isEmpty ? 'PYG' : _items.values.first.$1.currency;

  @override
  void dispose() {
    _debounce?.cancel();
    _searchController.dispose();
    for (final line in _payments) {
      line.dispose();
    }
    super.dispose();
  }

  void _onSearchChanged(String value) {
    _debounce?.cancel();
    _debounce = Timer(const Duration(milliseconds: 250), () {
      if (mounted) setState(() => _term = value.trim());
    });
  }

  /// Cuántas unidades puede cargar el cajero de este producto.
  ///
  /// Cuando el catálogo informa escasez real, ese número es el techo y evita
  /// facturar de más. Cuando informa el tope fijo, el producto tiene stock de
  /// sobra y no se pone límite: una venta de mostrador puede ser de 150
  /// unidades y `VentaDirectaRequest` no tiene máximo, así que topar en 99 acá
  /// sería inventar una restricción que el sistema no tiene.
  int _ceilingFor(Product product) =>
      product.showsRemainingUnits ? product.maxPerOrder : _sinTecho;

  /// Techo práctico para un producto con stock de sobra: alto para no estorbar,
  /// finito para que el selector tenga un máximo con el que trabajar.
  static const int _sinTecho = 9999;

  /// Agrega una unidad respetando lo que hay.
  ///
  /// Antes el `+` no tenía techo ninguno: en el mostrador se podían cargar más
  /// unidades de las que existen y el rechazo llegaba al confirmar, con el
  /// cliente esperando de pie.
  void _add(Product product) {
    final current = _items[product.id]?.$2 ?? 0;
    if (current >= _ceilingFor(product)) {
      _cue(HapticCue.error);
      _message(
        product.maxPerOrder <= 0
            ? '${product.name} no tiene unidades disponibles'
            : 'De ${product.name} solo hay ${product.maxPerOrder} '
                  '${product.maxPerOrder == 1 ? 'unidad' : 'unidades'}',
      );
      return;
    }
    setState(() {
      _items[product.id] = (product, current + 1);
      // Se limpia para cargar el siguiente artículo escribiendo.
      _searchController.clear();
      _term = '';
    });
    _cue(HapticCue.success);
  }

  @override
  Widget build(BuildContext context) {
    // La búsqueda va al servidor con el término escrito. Antes se filtraba en
    // memoria sobre la primera página del catálogo, así que los productos que
    // no entraban en esas 30 filas no se podían vender desde el mostrador.
    final products = ref.watch(catalogProvider((search: _term, brandId: null)));
    final theme = Theme.of(context);

    return Scaffold(
      appBar: AppBar(title: const Text('Venta directa')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          TextField(
            controller: _searchController,
            onChanged: _onSearchChanged,
            autofocus: true,
            decoration: InputDecoration(
              labelText: 'Buscar producto',
              helperText: 'Tocá un resultado para agregarlo a la venta',
              prefixIcon: const Icon(Icons.search),
              suffixIcon: VoiceDictationButton(
                controller: _searchController,
                tooltip: 'Buscar producto por voz',
                onFinalResult: (value) => setState(() => _term = value.trim()),
              ),
            ),
          ),
          const SizedBox(height: 8),
          products.when(
            loading: () => const Padding(
              padding: EdgeInsets.symmetric(vertical: 8),
              child: LinearProgressIndicator(),
            ),
            error: (error, _) => AppErrorState(
              message: 'No pudimos buscar productos. Revisá tu conexión.',
              onRetry: () => ref.invalidate(
                catalogProvider((search: _term, brandId: null)),
              ),
            ),
            data: (values) {
              final results = values
                  .where((product) => product.maxPerOrder > 0)
                  .take(8)
                  .toList(growable: false);
              if (results.isEmpty) {
                return Padding(
                  padding: const EdgeInsets.symmetric(vertical: 12),
                  child: Text(
                    _term.isEmpty
                        ? 'No hay productos con stock para vender.'
                        : 'No hay productos con stock que coincidan '
                              'con "$_term".',
                    style: theme.textTheme.bodyMedium?.copyWith(
                      color: theme.colorScheme.onSurfaceVariant,
                    ),
                  ),
                );
              }
              return Column(
                children: [
                  for (final product in results)
                    ListTile(
                      dense: true,
                      title: Text(product.name),
                      subtitle: Text(
                        // El número solo se muestra cuando dice cuánto queda:
                        // con stock de sobra, «99 disponibles» sería falso.
                        product.showsRemainingUnits
                            ? '${formatMoney(product.price, product.currency)}'
                                  ' · quedan ${product.maxPerOrder}'
                            : '${formatMoney(product.price, product.currency)}'
                                  ' · con stock',
                      ),
                      trailing: IconButton.filledTonal(
                        tooltip: 'Agregar ${product.name}',
                        onPressed: () => _add(product),
                        icon: const Icon(Icons.add),
                      ),
                      onTap: () => _add(product),
                    ),
                ],
              );
            },
          ),
          const Divider(height: 28),
          if (_items.isEmpty)
            Padding(
              padding: const EdgeInsets.symmetric(vertical: 12),
              child: Text(
                'Todavía no cargaste nada. Buscá un producto para empezar.',
                style: theme.textTheme.bodyMedium?.copyWith(
                  color: theme.colorScheme.onSurfaceVariant,
                ),
              ),
            ),
          for (final entry in _items.entries)
            ListTile(
              title: Text(entry.value.$1.name),
              subtitle: Text(
                '${formatMoney(entry.value.$1.price, entry.value.$1.currency)}'
                ' c/u · subtotal '
                '${formatMoney(entry.value.$1.price * entry.value.$2, entry.value.$1.currency)}',
              ),
              trailing: QuantityStepper(
                value: entry.value.$2,
                max: _ceilingFor(entry.value.$1),
                min: 0,
                dense: true,
                limitMeansScarcity: entry.value.$1.showsRemainingUnits,
                onChanged: (value) => _setQuantity(entry.key, value),
              ),
            ),
          const Divider(height: 32),
          Text(
            'Pagos · Total ${formatMoney(_total, _currency)}',
            style: theme.textTheme.titleLarge,
          ),
          for (var index = 0; index < _payments.length; index++)
            _PaymentEditor(
              // El medio se toma como valor inicial: sin la clave, al quitar
              // o renovar líneas un editor podía mostrar el medio de otra.
              key: ObjectKey(_payments[index]),
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
        return;
      }
      final product = _items[id]!.$1;
      // El selector ya no ofrece más que el tope, pero acá se recorta igual:
      // esta lista es la que se factura, y no debe poder quedar por encima de
      // lo que hay ni por un camino que no pasó por el selector.
      final capped = quantity > _ceilingFor(product)
          ? _ceilingFor(product)
          : quantity;
      if (capped <= 0) {
        _items.remove(id);
      } else {
        _items[id] = (product, capped);
      }
    });
    _cue(HapticCue.tap);
  }

  void _cue(HapticCue cue) {
    unawaited(ref.read(hapticsServiceProvider).play(cue));
  }

  Future<void> _submit() async {
    if (_busy) return;
    final payments = <Map<String, Object?>>[];
    for (final line in _payments) {
      // El campo muestra el monto agrupado; lo que viaja es el numero.
      final amount = parsearMonto(line.amount.text, decimales: 2);
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
    if (!mounted) return;
    setState(() => _busy = true);
    try {
      final confirmed = await confirmarOperacion(
        context,
        ref,
        reason: 'Confirmá la venta por ${formatMoney(_total, _currency)}',
        titulo: 'Confirmar venta',
      );
      if (!mounted) return;
      if (!confirmed) {
        _cue(HapticCue.error);
        _message('Venta cancelada: no se validó la identidad');
        return;
      }
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
      if (!mounted) return;
      _items.clear();
      // Los pagos eran de la venta recién facturada: si quedaban, la
      // siguiente arrancaba con el monto anterior ya cargado.
      for (final line in _payments) {
        line.dispose();
      }
      _payments
        ..clear()
        ..add(_PaymentLine());
      _cue(HapticCue.success);
      _message(_avisoDeVenta(response.data));
      setState(() {});
    } on AppFailure catch (failure) {
      if (!mounted) return;
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

/// El servidor responde con el pago: el número de la venta viene en
/// `pedidoNumero` y el de la factura dentro de `factura`. Se leía `numero`,
/// que no existe, y el aviso decía «Venta #null registrada».
String _avisoDeVenta(Map<String, Object?>? respuesta) {
  final numero = respuesta?['pedidoNumero'];
  final factura = respuesta?['factura'];
  final numeroDeFactura = factura is Map ? factura['numero'] : null;
  return [
    numero == null ? 'Venta registrada' : 'Venta N.º $numero registrada',
    if (numeroDeFactura != null) 'Factura $numeroDeFactura',
  ].join(' · ');
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
    super.key,
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
              DropdownMenuItem(
                value: 'Transferencia',
                child: Text('Transferencia'),
              ),
            ],
            onChanged: (value) => setState(() => widget.line.type = value!),
          ),
          MoneyField(
            controller: widget.line.amount,
            label: 'Monto',
            decimales: 2,
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
