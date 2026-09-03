import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';

import '../../../core/errors/app_failure.dart';
import '../../cart/application/cart_controller.dart';
import '../application/checkout_providers.dart';
import '../domain/checkout_models.dart';

class CheckoutPage extends ConsumerStatefulWidget {
  const CheckoutPage({super.key});

  @override
  ConsumerState<CheckoutPage> createState() => _CheckoutPageState();
}

class _CheckoutPageState extends ConsumerState<CheckoutPage> {
  bool _delivery = false;
  int? _addressId;
  ShippingQuote? _quote;
  String _payment = 'PAGO_EN_LOCAL';
  final _notes = TextEditingController();
  bool _busy = false;

  @override
  void dispose() {
    _notes.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final cart = ref.watch(cartProvider);
    final addresses = ref.watch(addressesProvider);
    return Scaffold(
      appBar: AppBar(title: const Text('Confirmar compra')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Text('Entrega', style: Theme.of(context).textTheme.titleLarge),
          RadioGroup<bool>(
            groupValue: _delivery,
            onChanged: _busy
                ? (_) {}
                : (value) {
                    if (value != null) {
                      setState(() {
                        _delivery = value;
                        if (_delivery && _payment == 'PAGO_EN_LOCAL') {
                          _payment = 'PAGO_EFECTIVO_REPARTIDOR';
                        } else if (!_delivery &&
                            _payment == 'PAGO_EFECTIVO_REPARTIDOR') {
                          _payment = 'PAGO_EN_LOCAL';
                        }
                      });
                    }
                  },
            child: const Column(
              children: [
                RadioListTile<bool>(
                  value: false,
                  title: Text('Retiro en tienda'),
                ),
                RadioListTile<bool>(
                  value: true,
                  title: Text('Envío a domicilio'),
                ),
              ],
            ),
          ),
          if (_delivery) ...[
            addresses.when(
              loading: () => const LinearProgressIndicator(),
              error: (error, _) =>
                  const Text('No se pudieron cargar las direcciones'),
              data: (items) => items.isEmpty
                  ? OutlinedButton.icon(
                      onPressed: () => context.push('/addresses'),
                      icon: const Icon(Icons.add_location_alt_outlined),
                      label: const Text('Agregar dirección'),
                    )
                  : DropdownButtonFormField<int>(
                      initialValue: _addressId,
                      decoration: const InputDecoration(labelText: 'Dirección'),
                      items: [
                        for (final address in items)
                          DropdownMenuItem(
                            value: address.id,
                            child: Text('${address.line1}, ${address.city}'),
                          ),
                      ],
                      onChanged: (value) {
                        setState(() {
                          _addressId = value;
                          _quote = null;
                        });
                        if (value != null) _selectQuote(cart, value);
                      },
                    ),
            ),
            if (_quote != null)
              ListTile(
                leading: const Icon(Icons.local_shipping_outlined),
                title: Text(_quote!.name),
                subtitle: Text(_dateRange(_quote!)),
                trailing: Text(_money(_quote!.cost, _quote!.currency)),
              ),
          ],
          const SizedBox(height: 20),
          Text('Pago', style: Theme.of(context).textTheme.titleLarge),
          DropdownButtonFormField<String>(
            key: ValueKey('payment-$_delivery'),
            initialValue: _payment,
            decoration: const InputDecoration(labelText: 'Método de pago'),
            items: [
              if (!_delivery)
                const DropdownMenuItem(
                  value: 'PAGO_EN_LOCAL',
                  child: Text('Pago en el local'),
                ),
              if (_delivery)
                const DropdownMenuItem(
                  value: 'PAGO_EFECTIVO_REPARTIDOR',
                  child: Text('Efectivo al repartidor'),
                ),
              DropdownMenuItem(
                value: 'TRANSFERENCIA_BANCARIA',
                child: Text('Transferencia bancaria'),
              ),
            ],
            onChanged: _busy
                ? null
                : (value) => setState(() => _payment = value!),
          ),
          const SizedBox(height: 16),
          TextField(
            controller: _notes,
            maxLength: 1000,
            maxLines: 3,
            decoration: const InputDecoration(labelText: 'Notas (opcional)'),
          ),
          const SizedBox(height: 16),
          FilledButton.icon(
            onPressed: _busy || cart.isEmpty || (_delivery && _quote == null)
                ? null
                : () => _submit(cart),
            icon: _busy
                ? const SizedBox.square(
                    dimension: 18,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : const Icon(Icons.lock_outline),
            label: const Text('Crear pedido'),
          ),
        ],
      ),
    );
  }

  Future<void> _selectQuote(List<CartLine> cart, int addressId) async {
    try {
      final quotes = await ref
          .read(checkoutRepositoryProvider)
          .quotes(cart, addressId: addressId);
      if (!mounted) return;
      if (quotes.isEmpty) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('No hay opciones de envío disponibles')),
        );
        return;
      }
      final selected = await showModalBottomSheet<ShippingQuote>(
        context: context,
        builder: (context) => SafeArea(
          child: ListView(
            shrinkWrap: true,
            children: [
              for (final quote in quotes)
                ListTile(
                  title: Text(quote.name),
                  subtitle: Text(_dateRange(quote)),
                  trailing: Text(_money(quote.cost, quote.currency)),
                  onTap: () => Navigator.pop(context, quote),
                ),
            ],
          ),
        ),
      );
      if (selected != null && mounted) setState(() => _quote = selected);
    } on AppFailure catch (failure) {
      if (mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text(failure.message)));
      }
    }
  }

  Future<void> _submit(List<CartLine> cart) async {
    setState(() => _busy = true);
    try {
      final repository = ref.read(checkoutRepositoryProvider);
      final validation = await repository.validate(cart);
      if (validation['valido'] != true) {
        throw const ValidationFailure(
          'El carrito cambió; revisá precios y disponibilidad.',
        );
      }
      final id = await repository.checkout(
        cart,
        delivery: _delivery,
        addressId: _addressId,
        tariffId: _quote?.tariffId,
        paymentMethod: _payment,
        notes: _notes.text,
      );
      ref.read(cartProvider.notifier).clear();
      ref.invalidate(ordersProvider);
      if (mounted) context.go('/orders/$id');
    } on AppFailure catch (failure) {
      if (mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text(failure.message)));
      }
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }
}

String _money(double value, String currency) => NumberFormat.currency(
  locale: 'es_PY',
  symbol: currency == 'PYG' ? '₲' : currency,
  decimalDigits: currency == 'PYG' ? 0 : 2,
).format(value);

String _dateRange(ShippingQuote quote) {
  if (quote.estimatedFrom == null) return 'Plazo sujeto a confirmación';
  return '${quote.estimatedFrom} — ${quote.estimatedTo ?? quote.estimatedFrom}';
}
