import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';

import '../../../core/errors/app_failure.dart';
import '../../../core/format/money.dart';
import '../../cart/application/cart_controller.dart';
import '../application/checkout_providers.dart';
import '../domain/checkout_models.dart';

/// Cómo se entrega el pedido. Los dos caminos no comparten opciones: cada uno
/// muestra solo lo suyo para que no haya que descartar lo que no aplica.
enum _Delivery { pickup, shipping }

/// Formas de pago, con el texto que ve el cliente y en qué entrega aplican.
enum _Payment {
  atStore('PAGO_EN_LOCAL', 'Pago al retirar', Icons.storefront_outlined),
  cashOnDelivery(
    'PAGO_EFECTIVO_REPARTIDOR',
    'Efectivo al recibir',
    Icons.payments_outlined,
  ),
  bankTransfer(
    'TRANSFERENCIA_BANCARIA',
    'Transferencia bancaria',
    Icons.account_balance_outlined,
  );

  const _Payment(this.code, this.label, this.icon);

  final String code;
  final String label;
  final IconData icon;

  /// Pagar en el local no existe si el pedido va a domicilio, y pagarle al
  /// repartidor no existe si el cliente va a retirar.
  bool availableFor(_Delivery delivery) => switch (this) {
    _Payment.atStore => delivery == _Delivery.pickup,
    _Payment.cashOnDelivery => delivery == _Delivery.shipping,
    _Payment.bankTransfer => true,
  };

  static List<_Payment> forDelivery(_Delivery delivery) =>
      _Payment.values.where((p) => p.availableFor(delivery)).toList();
}

class CheckoutPage extends ConsumerStatefulWidget {
  const CheckoutPage({super.key});

  @override
  ConsumerState<CheckoutPage> createState() => _CheckoutPageState();
}

class _CheckoutPageState extends ConsumerState<CheckoutPage> {
  /// Paso actual: 0 entrega, 1 pago, 2 confirmación. Los productos ya se
  /// eligieron en el carrito, así que ese paso se muestra como cumplido.
  int _step = 0;

  _Delivery _delivery = _Delivery.pickup;
  int? _addressId;
  ShippingQuote? _quote;
  _Payment _payment = _Payment.atStore;
  final _notes = TextEditingController();

  bool _busy = false;
  String? _blockingMessage;

  @override
  void dispose() {
    _notes.dispose();
    super.dispose();
  }

  bool get _deliveryReady =>
      _delivery == _Delivery.pickup || (_addressId != null && _quote != null);

  void _setDelivery(_Delivery value) {
    setState(() {
      _delivery = value;
      // Al cambiar de entrega, la forma de pago elegida puede dejar de existir.
      if (!_payment.availableFor(value)) {
        _payment = _Payment.forDelivery(value).first;
      }
      if (value == _Delivery.pickup) {
        _quote = null;
        _addressId = null;
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    final cart = ref.watch(cartProvider);
    if (cart.isEmpty) return const _EmptyCheckout();

    return Scaffold(
      appBar: AppBar(
        title: const Text('Finalizar compra'),
        bottom: PreferredSize(
          preferredSize: const Size.fromHeight(52),
          child: _Steps(current: _step),
        ),
      ),
      body: AbsorbPointer(
        absorbing: _busy,
        child: ListView(
          padding: const EdgeInsets.fromLTRB(16, 16, 16, 32),
          children: [
            if (_blockingMessage != null) ...[
              _Notice(message: _blockingMessage!),
              const SizedBox(height: 16),
            ],
            switch (_step) {
              0 => _DeliveryStep(
                delivery: _delivery,
                addressId: _addressId,
                quote: _quote,
                onDeliveryChanged: _setDelivery,
                onAddressChanged: (value) {
                  setState(() {
                    _addressId = value;
                    _quote = null;
                  });
                  if (value != null) _selectQuote(cart, value);
                },
                onPickQuote: () {
                  final id = _addressId;
                  if (id != null) _selectQuote(cart, id);
                },
              ),
              1 => _PaymentStep(
                delivery: _delivery,
                payment: _payment,
                notes: _notes,
                onChanged: (value) => setState(() => _payment = value),
              ),
              _ => _ConfirmStep(
                cart: cart,
                delivery: _delivery,
                quote: _quote,
                payment: _payment,
                notes: _notes.text,
              ),
            },
          ],
        ),
      ),
      bottomNavigationBar: _Footer(
        step: _step,
        busy: _busy,
        canContinue: switch (_step) {
          0 => _deliveryReady,
          1 => true,
          _ => true,
        },
        onBack: _busy
            ? null
            : () {
                if (_step == 0) {
                  context.pop();
                } else {
                  setState(() => _step -= 1);
                }
              },
        onNext: _busy
            ? null
            : () {
                if (_step < 2) {
                  setState(() => _step += 1);
                } else {
                  _submit(cart);
                }
              },
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
        _toast(
          'No hay envíos disponibles a esa dirección. '
          'Probá con otra o elegí retirar en la tienda.',
        );
        return;
      }
      final selected = await showModalBottomSheet<ShippingQuote>(
        context: context,
        showDragHandle: true,
        builder: (context) => SafeArea(
          child: ListView(
            shrinkWrap: true,
            children: [
              Padding(
                padding: const EdgeInsets.fromLTRB(16, 0, 16, 8),
                child: Text(
                  'Elegí cómo querés recibirlo',
                  style: Theme.of(context).textTheme.titleMedium,
                ),
              ),
              for (final quote in quotes)
                ListTile(
                  leading: const Icon(Icons.local_shipping_outlined),
                  title: Text(quote.name),
                  subtitle: Text(_dateRange(quote)),
                  trailing: Text(formatMoney(quote.cost, quote.currency)),
                  onTap: () => Navigator.pop(context, quote),
                ),
            ],
          ),
        ),
      );
      if (selected != null && mounted) setState(() => _quote = selected);
    } on AppFailure catch (failure) {
      if (mounted) _toast(failure.message);
    }
  }

  Future<void> _submit(List<CartLine> cart) async {
    setState(() {
      _busy = true;
      _blockingMessage = null;
    });
    try {
      final repository = ref.read(checkoutRepositoryProvider);

      // Última revisión antes de comprometer stock: entre que armó el carrito
      // y este momento pudo cambiar la disponibilidad.
      final availability = await repository.availability(cart);
      if (!availability.valid) {
        final report = ref
            .read(cartProvider.notifier)
            .applyAvailability(availability.maxByProduct);
        if (!mounted) return;
        final remaining = ref.read(cartProvider);
        setState(() {
          _step = 2;
          _blockingMessage = report.hasChanges
              ? report.message
              : 'La disponibilidad de tu carrito cambió. Revisá las '
                    'cantidades antes de confirmar.';
        });
        if (remaining.isEmpty && mounted) context.go('/cart');
        return;
      }

      final id = await repository.checkout(
        ref.read(cartProvider),
        delivery: _delivery == _Delivery.shipping,
        addressId: _addressId,
        tariffId: _quote?.tariffId,
        paymentMethod: _payment.code,
        notes: _notes.text,
      );
      ref.read(cartProvider.notifier).clear();
      ref.invalidate(ordersProvider);
      if (!mounted) return;
      _toast('¡Listo! Tu pedido quedó confirmado.');
      context.go('/orders/$id');
    } on AppFailure catch (failure) {
      if (mounted) setState(() => _blockingMessage = failure.message);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  void _toast(String message) {
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(
        SnackBar(content: Text(message), duration: const Duration(seconds: 4)),
      );
  }
}

class _Steps extends StatelessWidget {
  const _Steps({required this.current});

  final int current;

  static const _labels = ['Entrega', 'Pago', 'Confirmación'];

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 0, 16, 12),
      child: Row(
        children: [
          for (var i = 0; i < _labels.length; i++) ...[
            if (i > 0) const SizedBox(width: 8),
            Expanded(
              child: Semantics(
                selected: i == current,
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    // Además del color, el paso actual se marca con la barra
                    // más gruesa y el texto en negrita.
                    Container(
                      height: i == current ? 5 : 3,
                      decoration: BoxDecoration(
                        color: i <= current
                            ? theme.colorScheme.primary
                            : theme.colorScheme.surfaceContainerHighest,
                        borderRadius: BorderRadius.circular(3),
                      ),
                    ),
                    const SizedBox(height: 5),
                    Text(
                      '${i + 1}. ${_labels[i]}',
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: theme.textTheme.labelSmall?.copyWith(
                        fontWeight: i == current
                            ? FontWeight.bold
                            : FontWeight.normal,
                        color: i <= current
                            ? theme.colorScheme.onSurface
                            : theme.colorScheme.onSurfaceVariant,
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ],
        ],
      ),
    );
  }
}

class _DeliveryStep extends ConsumerWidget {
  const _DeliveryStep({
    required this.delivery,
    required this.addressId,
    required this.quote,
    required this.onDeliveryChanged,
    required this.onAddressChanged,
    required this.onPickQuote,
  });

  final _Delivery delivery;
  final int? addressId;
  final ShippingQuote? quote;
  final ValueChanged<_Delivery> onDeliveryChanged;
  final ValueChanged<int?> onAddressChanged;
  final VoidCallback onPickQuote;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final addresses = ref.watch(addressesProvider);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          '¿Cómo querés recibir tu pedido?',
          style: Theme.of(context).textTheme.titleMedium,
        ),
        const SizedBox(height: 12),
        _ChoiceCard(
          selected: delivery == _Delivery.pickup,
          icon: Icons.storefront_outlined,
          title: 'Retiro en la tienda',
          subtitle: 'Lo buscás vos, sin costo de envío',
          onTap: () => onDeliveryChanged(_Delivery.pickup),
        ),
        const SizedBox(height: 8),
        _ChoiceCard(
          selected: delivery == _Delivery.shipping,
          icon: Icons.local_shipping_outlined,
          title: 'Envío a domicilio',
          subtitle: 'Te lo llevamos a la dirección que elijas',
          onTap: () => onDeliveryChanged(_Delivery.shipping),
        ),
        if (delivery == _Delivery.shipping) ...[
          const SizedBox(height: 20),
          Text('Dirección', style: Theme.of(context).textTheme.titleSmall),
          const SizedBox(height: 8),
          addresses.when(
            loading: () => const Padding(
              padding: EdgeInsets.symmetric(vertical: 8),
              child: LinearProgressIndicator(),
            ),
            error: (error, _) => _Notice(
              message:
                  'No pudimos cargar tus direcciones. '
                  'Revisá tu conexión e intentá de nuevo.',
            ),
            data: (items) => items.isEmpty
                ? _Notice(
                    message: 'Todavía no cargaste ninguna dirección.',
                    action: FilledButton.tonalIcon(
                      onPressed: () => context.push('/addresses'),
                      icon: const Icon(Icons.add_location_alt_outlined),
                      label: const Text('Agregar dirección'),
                    ),
                  )
                : DropdownButtonFormField<int>(
                    initialValue: addressId,
                    isExpanded: true,
                    decoration: const InputDecoration(
                      labelText: 'Dirección de entrega',
                      helperText: 'Elegí dónde querés recibir el pedido',
                    ),
                    items: [
                      for (final address in items)
                        DropdownMenuItem(
                          value: address.id,
                          child: Text(
                            '${address.line1}, ${address.city}',
                            overflow: TextOverflow.ellipsis,
                          ),
                        ),
                    ],
                    onChanged: onAddressChanged,
                  ),
          ),
          if (addressId != null) ...[
            const SizedBox(height: 12),
            if (quote == null)
              OutlinedButton.icon(
                onPressed: onPickQuote,
                icon: const Icon(Icons.local_shipping_outlined),
                label: const Text('Elegir forma de envío'),
              )
            else
              Card(
                margin: EdgeInsets.zero,
                child: ListTile(
                  leading: const Icon(Icons.local_shipping_outlined),
                  title: Text(quote!.name),
                  subtitle: Text(_dateRange(quote!)),
                  trailing: Text(formatMoney(quote!.cost, quote!.currency)),
                  onTap: onPickQuote,
                ),
              ),
          ],
        ],
      ],
    );
  }
}

class _PaymentStep extends StatelessWidget {
  const _PaymentStep({
    required this.delivery,
    required this.payment,
    required this.notes,
    required this.onChanged,
  });

  final _Delivery delivery;
  final _Payment payment;
  final TextEditingController notes;
  final ValueChanged<_Payment> onChanged;

  @override
  Widget build(BuildContext context) {
    final options = _Payment.forDelivery(delivery);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          '¿Cómo querés pagar?',
          style: Theme.of(context).textTheme.titleMedium,
        ),
        const SizedBox(height: 12),
        for (final option in options) ...[
          _ChoiceCard(
            selected: option == payment,
            icon: option.icon,
            title: option.label,
            subtitle: switch (option) {
              _Payment.atStore => 'Abonás cuando pasás a buscarlo',
              _Payment.cashOnDelivery => 'Le pagás al repartidor en la puerta',
              _Payment.bankTransfer =>
                'Te pasamos los datos al confirmar el pedido',
            },
            onTap: () => onChanged(option),
          ),
          const SizedBox(height: 8),
        ],
        const SizedBox(height: 12),
        TextField(
          controller: notes,
          maxLength: 1000,
          maxLines: 3,
          decoration: const InputDecoration(
            labelText: 'Aclaraciones para el pedido',
            helperText: 'Opcional. Por ejemplo, una referencia para llegar.',
          ),
        ),
      ],
    );
  }
}

class _ConfirmStep extends StatelessWidget {
  const _ConfirmStep({
    required this.cart,
    required this.delivery,
    required this.quote,
    required this.payment,
    required this.notes,
  });

  final List<CartLine> cart;
  final _Delivery delivery;
  final ShippingQuote? quote;
  final _Payment payment;
  final String notes;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final totals = totalsByCurrency([
      for (final line in cart)
        (amount: line.subtotal, currency: line.product.currency),
      if (quote != null) (amount: quote!.cost, currency: quote!.currency),
    ]);
    final subtotals = totalsByCurrency([
      for (final line in cart)
        (amount: line.subtotal, currency: line.product.currency),
    ]);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text('Revisá tu pedido', style: theme.textTheme.titleMedium),
        const SizedBox(height: 12),
        Card(
          margin: EdgeInsets.zero,
          child: Padding(
            padding: const EdgeInsets.all(12),
            child: Column(
              children: [
                for (final line in cart)
                  Padding(
                    padding: const EdgeInsets.symmetric(vertical: 4),
                    child: Row(
                      children: [
                        Expanded(
                          child: Text(
                            '${line.quantity} × ${line.product.name}',
                            style: theme.textTheme.bodyMedium,
                          ),
                        ),
                        Text(
                          formatMoney(line.subtotal, line.product.currency),
                          style: theme.textTheme.bodyMedium,
                        ),
                      ],
                    ),
                  ),
                const Divider(),
                _SummaryRow(label: 'Subtotal', value: formatTotals(subtotals)),
                _SummaryRow(
                  label: 'Envío',
                  value: delivery == _Delivery.pickup
                      ? 'Sin costo (retirás vos)'
                      : quote == null
                      ? 'A confirmar'
                      : formatMoney(quote!.cost, quote!.currency),
                ),
                const Divider(),
                _SummaryRow(
                  label: 'Total',
                  value: formatTotals(totals),
                  emphasize: true,
                ),
              ],
            ),
          ),
        ),
        const SizedBox(height: 16),
        _DetailTile(
          icon: delivery == _Delivery.pickup
              ? Icons.storefront_outlined
              : Icons.local_shipping_outlined,
          title: delivery == _Delivery.pickup
              ? 'Retiro en la tienda'
              : 'Envío a domicilio',
          subtitle: delivery == _Delivery.shipping && quote != null
              ? '${quote!.name} · ${_dateRange(quote!)}'
              : null,
        ),
        _DetailTile(icon: payment.icon, title: payment.label),
        if (notes.trim().isNotEmpty)
          _DetailTile(
            icon: Icons.sticky_note_2_outlined,
            title: 'Tus aclaraciones',
            subtitle: notes.trim(),
          ),
      ],
    );
  }
}

class _SummaryRow extends StatelessWidget {
  const _SummaryRow({
    required this.label,
    required this.value,
    this.emphasize = false,
  });

  final String label;
  final String value;
  final bool emphasize;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final style = emphasize
        ? theme.textTheme.titleMedium
        : theme.textTheme.bodyMedium?.copyWith(
            color: theme.colorScheme.onSurfaceVariant,
          );
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 3),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(label, style: style),
          Text(value, style: style),
        ],
      ),
    );
  }
}

class _DetailTile extends StatelessWidget {
  const _DetailTile({required this.icon, required this.title, this.subtitle});

  final IconData icon;
  final String title;
  final String? subtitle;

  @override
  Widget build(BuildContext context) => ListTile(
    contentPadding: EdgeInsets.zero,
    leading: Icon(icon),
    title: Text(title),
    subtitle: subtitle == null ? null : Text(subtitle!),
  );
}

class _ChoiceCard extends StatelessWidget {
  const _ChoiceCard({
    required this.selected,
    required this.icon,
    required this.title,
    required this.subtitle,
    required this.onTap,
  });

  final bool selected;
  final IconData icon;
  final String title;
  final String subtitle;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Card(
      margin: EdgeInsets.zero,
      // La selección no se comunica solo con color: hay borde marcado y una
      // tilde, para que se distinga sin depender de la vista del color.
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(12),
        side: BorderSide(
          color: selected
              ? theme.colorScheme.primary
              : theme.colorScheme.outlineVariant,
          width: selected ? 2 : 1,
        ),
      ),
      child: InkWell(
        borderRadius: BorderRadius.circular(12),
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.all(12),
          child: Row(
            children: [
              Icon(icon, color: selected ? theme.colorScheme.primary : null),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(title, style: theme.textTheme.titleSmall),
                    const SizedBox(height: 2),
                    Text(
                      subtitle,
                      style: theme.textTheme.bodySmall?.copyWith(
                        color: theme.colorScheme.onSurfaceVariant,
                      ),
                    ),
                  ],
                ),
              ),
              if (selected)
                Icon(Icons.check_circle, color: theme.colorScheme.primary),
            ],
          ),
        ),
      ),
    );
  }
}

class _Notice extends StatelessWidget {
  const _Notice({required this.message, this.action});

  final String message;
  final Widget? action;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: theme.colorScheme.secondaryContainer,
        borderRadius: BorderRadius.circular(12),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Icon(
                Icons.info_outline,
                size: 20,
                color: theme.colorScheme.onSecondaryContainer,
              ),
              const SizedBox(width: 8),
              Expanded(
                child: Text(
                  message,
                  style: theme.textTheme.bodyMedium?.copyWith(
                    color: theme.colorScheme.onSecondaryContainer,
                  ),
                ),
              ),
            ],
          ),
          if (action != null) ...[const SizedBox(height: 10), action!],
        ],
      ),
    );
  }
}

class _Footer extends StatelessWidget {
  const _Footer({
    required this.step,
    required this.busy,
    required this.canContinue,
    required this.onBack,
    required this.onNext,
  });

  final int step;
  final bool busy;
  final bool canContinue;
  final VoidCallback? onBack;
  final VoidCallback? onNext;

  @override
  Widget build(BuildContext context) {
    final isLast = step == 2;
    return SafeArea(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Row(
          children: [
            // Flexible y no Expanded: con un tercio fijo del ancho, «Volver al
            // carrito» se partía en tres renglones y el botón quedaba el doble
            // de alto que el principal. Así toma lo que necesita y cede solo
            // si de verdad no entra.
            Flexible(
              child: OutlinedButton(
                onPressed: onBack,
                // Con el relleno de 24 px por lado que trae Material, «Volver
                // al carrito» se pasaba por unos pocos píxeles y se cortaba en
                // «Volver al car…». El área táctil sigue por encima del mínimo.
                style: OutlinedButton.styleFrom(
                  padding: const EdgeInsets.symmetric(horizontal: 12),
                ),
                child: Text(
                  step == 0 ? 'Volver al carrito' : 'Atrás',
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
              ),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: FilledButton.icon(
                // Mientras procesa queda deshabilitado: evita que dos toques
                // generen dos pedidos.
                onPressed: busy || !canContinue ? null : onNext,
                icon: busy
                    ? const SizedBox.square(
                        dimension: 18,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      )
                    : Icon(
                        isLast
                            ? Icons.check_circle_outline
                            : Icons.arrow_forward,
                      ),
                label: Text(
                  busy
                      ? 'Procesando pedido...'
                      : isLast
                      ? 'Confirmar compra'
                      : 'Continuar',
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _EmptyCheckout extends StatelessWidget {
  const _EmptyCheckout();

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Scaffold(
      appBar: AppBar(title: const Text('Finalizar compra')),
      body: Center(
        child: Padding(
          padding: const EdgeInsets.all(32),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              ExcludeSemantics(
                child: Icon(
                  Icons.shopping_cart_outlined,
                  size: 64,
                  color: theme.colorScheme.onSurfaceVariant,
                ),
              ),
              const SizedBox(height: 16),
              Text(
                'No hay nada para comprar',
                style: theme.textTheme.titleMedium,
              ),
              const SizedBox(height: 8),
              Text(
                'Agregá productos al carrito y volvé para terminar la compra.',
                textAlign: TextAlign.center,
                style: theme.textTheme.bodyMedium?.copyWith(
                  color: theme.colorScheme.onSurfaceVariant,
                ),
              ),
              const SizedBox(height: 24),
              FilledButton.icon(
                onPressed: () => context.go('/catalog'),
                icon: const Icon(Icons.storefront_outlined),
                label: const Text('Ver productos'),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

/// Fechas del envío en lenguaje corriente. Antes se mostraba el texto crudo que
/// devuelve el servidor (`2026-09-05`), que no es como una persona lee una
/// fecha.
String _dateRange(ShippingQuote quote) {
  final from = _parseDate(quote.estimatedFrom);
  if (from == null) return 'Fecha a confirmar';
  final to = _parseDate(quote.estimatedTo);
  final formatter = DateFormat('d MMM', 'es');
  if (to == null || to == from) return 'Llega el ${formatter.format(from)}';
  return 'Llega entre el ${formatter.format(from)} y el ${formatter.format(to)}';
}

DateTime? _parseDate(String? raw) {
  if (raw == null || raw.trim().isEmpty) return null;
  return DateTime.tryParse(raw.trim());
}
