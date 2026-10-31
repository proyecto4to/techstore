import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';
import 'package:uuid/uuid.dart';

import '../../../core/di/providers.dart';
import '../../../core/diseno/medidas.dart';
import '../../../core/diseno/movimiento.dart';
import '../../../core/diseno/paleta.dart';
import '../../../core/errors/app_failure.dart';
import '../../../core/format/money.dart';
import '../../../core/native/haptics_service.dart';
import '../../../core/native/receipt_picker.dart';
import '../../cart/application/cart_controller.dart';
import '../../payments/application/payments_providers.dart';
import '../../payments/domain/payment_models.dart';
import '../../payments/presentation/receipt_widgets.dart';
import '../../payments/presentation/transfer_instructions.dart';
import '../application/checkout_draft.dart';
import '../application/checkout_providers.dart';
import '../domain/checkout_models.dart';

/// Cuántos comprobantes se pueden adjuntar. Lo mismo que valida el servidor.
const _maximoComprobantes = 3;

class CheckoutPage extends ConsumerStatefulWidget {
  const CheckoutPage({super.key});

  @override
  ConsumerState<CheckoutPage> createState() => _CheckoutPageState();
}

class _CheckoutPageState extends ConsumerState<CheckoutPage> {
  late final TextEditingController _notes;
  bool _busy = false;
  String? _blockingMessage;

  @override
  void initState() {
    super.initState();
    _notes = TextEditingController(text: ref.read(checkoutDraftProvider).notes);
  }

  @override
  void dispose() {
    _notes.dispose();
    super.dispose();
  }

  CheckoutDraftController get _draft =>
      ref.read(checkoutDraftProvider.notifier);

  /// Lo que cuesta todo junto, tal como lo va a calcular el servidor.
  ({double total, String currency}) _total(
    List<CartLine> cart,
    CheckoutDraft draft,
  ) {
    final currency = cart.isEmpty ? 'PYG' : cart.first.product.currency;
    var total = 0.0;
    for (final line in cart) {
      total += line.subtotal;
    }
    if (draft.delivery == CheckoutDelivery.shipping && draft.quote != null) {
      total += draft.quote!.cost;
    }
    return (total: total, currency: currency);
  }

  @override
  Widget build(BuildContext context) {
    final cart = ref.watch(cartProvider);
    final draft = ref.watch(checkoutDraftProvider);
    // Si el carrito cambia mientras se compra, es otra compra: otra clave.
    ref.listen(cartProvider, (_, _) => _draft.invalidateKey());
    if (cart.isEmpty) return const _EmptyCheckout();

    final transfer = ref.watch(transferDetailsProvider);
    final transferAvailable = transfer.value?.available ?? false;
    final total = _total(cart, draft);

    final canContinue = switch (draft.step) {
      0 =>
        draft.deliveryReady &&
            (draft.delivery == CheckoutDelivery.pickup || transferAvailable),
      1 =>
        draft.paymentReady &&
            (draft.payment != CheckoutPayment.bankTransfer ||
                transferAvailable),
      _ => true,
    };

    return Scaffold(
      appBar: AppBar(
        title: const Text('Finalizar compra'),
        bottom: PreferredSize(
          preferredSize: const Size.fromHeight(52),
          child: _Steps(current: draft.step),
        ),
      ),
      body: AbsorbPointer(
        absorbing: _busy,
        child: AnimatedSwitcher(
          duration: Movimiento.o(context, Movimiento.medio),
          switchInCurve: Movimiento.estandar,
          transitionBuilder: (child, animation) => FadeTransition(
            opacity: animation,
            child: SlideTransition(
              position: Tween(
                begin: const Offset(.04, 0),
                end: Offset.zero,
              ).animate(animation),
              child: child,
            ),
          ),
          child: ListView(
            key: ValueKey(draft.step),
            padding: const EdgeInsets.fromLTRB(16, 16, 16, 32),
            children: [
              if (_blockingMessage != null) ...[
                _Notice(message: _blockingMessage!, error: true),
                const SizedBox(height: 16),
              ],
              switch (draft.step) {
                0 => _DeliveryStep(
                  draft: draft,
                  transfer: transfer,
                  onDeliveryChanged: _draft.setDelivery,
                  onAddressChanged: (value) {
                    _draft.setAddress(value);
                    if (value != null) _selectQuote(cart, value);
                  },
                  onPickQuote: () {
                    final id = draft.addressId;
                    if (id != null) _selectQuote(cart, id);
                  },
                ),
                1 => _PaymentStep(
                  draft: draft,
                  transfer: transfer,
                  amount: total.total,
                  currency: total.currency,
                  notes: _notes,
                  onChanged: _draft.setPayment,
                  onNotesChanged: _draft.setNotes,
                  onAddReceipt: _addReceipt,
                  onReplaceReceipt: _replaceReceipt,
                  onRemoveReceipt: _removeReceipt,
                  onRetryReceipt: _retryReceipt,
                ),
                _ => _ConfirmStep(
                  cart: cart,
                  draft: draft,
                  total: total.total,
                  currency: total.currency,
                ),
              },
            ],
          ),
        ),
      ),
      bottomNavigationBar: _Footer(
        step: draft.step,
        busy: _busy,
        canContinue: canContinue,
        onBack: _busy
            ? null
            : () {
                if (draft.step == 0) {
                  context.canPop() ? context.pop() : context.go('/cart');
                } else {
                  setState(() => _blockingMessage = null);
                  _draft.goTo(draft.step - 1);
                }
              },
        onNext: _busy
            ? null
            : () {
                if (draft.step < 2) {
                  setState(() => _blockingMessage = null);
                  _draft.goTo(draft.step + 1);
                } else {
                  _submit(cart);
                }
              },
      ),
    );
  }

  Future<void> _selectQuote(List<CartLine> cart, int addressId) async {
    try {
      final allQuotes = await ref
          .read(checkoutRepositoryProvider)
          .quotes(cart, addressId: addressId);
      // El servidor cotiza también el retiro, que ya tiene su propio camino.
      final quotes = allQuotes
          .where((quote) => quote.type != 'RETIRO_TIENDA')
          .toList(growable: false);
      final draft = ref.read(checkoutDraftProvider);
      if (!mounted ||
          draft.addressId != addressId ||
          draft.delivery != CheckoutDelivery.shipping) {
        return;
      }
      if (quotes.isEmpty) {
        _toast(
          'No hay envíos disponibles a esa dirección. '
          'Probá con otra o elegí retirar en la tienda.',
        );
        return;
      }
      // Con una sola tarifa no hay nada que elegir: abrir una hoja con una
      // opción es un toque de más.
      final selected = quotes.length == 1
          ? quotes.first
          : await showModalBottomSheet<ShippingQuote>(
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
      final now = ref.read(checkoutDraftProvider);
      if (selected != null &&
          mounted &&
          now.addressId == addressId &&
          now.delivery == CheckoutDelivery.shipping) {
        _draft.setQuote(selected);
      }
    } on AppFailure catch (failure) {
      if (mounted) _toast(failure.message);
    }
  }

  // Comprobantes.

  Future<void> _addReceipt() async {
    final picked = await _pickReceipt();
    if (picked == null) return;
    final receipt = DraftReceipt(localId: const Uuid().v4(), picked: picked);
    _draft.addReceipt(receipt);
    await _upload(receipt);
  }

  Future<void> _replaceReceipt(DraftReceipt old) async {
    final picked = await _pickReceipt();
    if (picked == null) return;
    await _removeReceipt(old, silencioso: true);
    final receipt = DraftReceipt(localId: const Uuid().v4(), picked: picked);
    _draft.addReceipt(receipt);
    await _upload(receipt);
  }

  Future<void> _retryReceipt(DraftReceipt receipt) async {
    _draft.updateReceipt(
      receipt.localId,
      (r) => r.copyWith(progress: 0, clearError: true),
    );
    await _upload(receipt);
  }

  Future<void> _removeReceipt(
    DraftReceipt receipt, {
    bool silencioso = false,
  }) async {
    _draft.removeReceipt(receipt.localId);
    final id = receipt.uploaded?.id;
    if (id == null) return;
    try {
      await ref.read(paymentsRepositoryProvider).deleteDraftReceipt(id);
    } on AppFailure {
      // Si no se pudo borrar en el servidor, el que quedó suelto se limpia solo
      // en un día; para esta compra ya no cuenta.
    }
    if (!silencioso) _toast('Comprobante quitado.');
  }

  Future<PickedReceipt?> _pickReceipt() async {
    final source = await pickReceiptSource(context);
    if (source == null || !mounted) return null;
    try {
      return await ref.read(receiptPickerProvider).pick(source);
    } on AppFailure catch (failure) {
      _toast(failure.message);
      return null;
    }
  }

  Future<void> _upload(DraftReceipt receipt) async {
    try {
      final uploaded = await ref
          .read(paymentsRepositoryProvider)
          .uploadDraftReceipt(
            receipt.picked,
            onProgress: (progreso) => _draft.updateReceipt(
              receipt.localId,
              (r) => r.copyWith(progress: progreso),
            ),
          );
      _draft.updateReceipt(
        receipt.localId,
        (r) => r.copyWith(uploaded: uploaded, progress: 1),
      );
      unawaited(ref.read(hapticsServiceProvider).play(HapticCue.success));
    } on AppFailure catch (failure) {
      _draft.updateReceipt(
        receipt.localId,
        (r) => r.copyWith(error: failure.message),
      );
    }
  }

  Future<void> _submit(List<CartLine> cart) async {
    // Dos toques pueden llegar antes de que se dibuje el botón deshabilitado.
    if (_busy) return;
    setState(() {
      _busy = true;
      _blockingMessage = null;
    });
    final haptics = ref.read(hapticsServiceProvider);
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
          _blockingMessage = report.hasChanges
              ? report.message
              : 'La disponibilidad de tu carrito cambió. Revisá las '
                    'cantidades antes de confirmar.';
        });
        // Se vuelve al carrito que abrió el checkout, no a uno nuevo: con
        // «go» se perdía la pila y el «atrás» siguiente cerraba la app.
        if (remaining.isEmpty && mounted) {
          context.canPop() ? context.pop() : context.go('/cart');
        }
        return;
      }

      final draft = ref.read(checkoutDraftProvider);
      final lines = ref.read(cartProvider);
      final porTransferencia = draft.payment == CheckoutPayment.bankTransfer;
      final id = await repository.checkout(
        lines,
        delivery: draft.delivery == CheckoutDelivery.shipping,
        addressId: draft.addressId,
        tariffId: draft.quote?.tariffId,
        paymentMethod: draft.payment.code,
        idempotencyKey: _draft.ensureKey(),
        notes: _notes.text,
        receiptIds: porTransferencia
            ? [for (final r in draft.uploadedReceipts) r.id]
            : const [],
        expectedTotal: porTransferencia ? _total(lines, draft).total : null,
      );
      ref.read(cartProvider.notifier).clear();
      _draft.reset();
      ref.invalidate(ordersProvider);
      // La vibración acompaña, no demora: navegar no espera a que termine.
      unawaited(haptics.play(HapticCue.success));
      if (!mounted) return;
      // Se abre el pedido con el aviso de que se acaba de hacer: ahí se ve el
      // número, en qué estado quedó el pago y qué sigue.
      context.go('/orders/$id?recien=1');
    } on AppFailure catch (failure) {
      await haptics.play(HapticCue.error);
      if (mounted) setState(() => _blockingMessage = failure.message);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  void _toast(String message) {
    if (!mounted) return;
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
                    AnimatedContainer(
                      duration: Movimiento.o(context, Movimiento.medio),
                      curve: Movimiento.estandar,
                      height: i == current ? 5 : 3,
                      decoration: BoxDecoration(
                        color: i == current
                            ? theme.colorScheme.secondary
                            : i < current
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
    required this.draft,
    required this.transfer,
    required this.onDeliveryChanged,
    required this.onAddressChanged,
    required this.onPickQuote,
  });

  final CheckoutDraft draft;
  final AsyncValue<TransferDetails> transfer;
  final ValueChanged<CheckoutDelivery> onDeliveryChanged;
  final ValueChanged<int?> onAddressChanged;
  final VoidCallback onPickQuote;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final addresses = ref.watch(addressesProvider);
    final shipping = draft.delivery == CheckoutDelivery.shipping;
    final sinTransferencia =
        transfer.hasValue && !(transfer.value?.available ?? false);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          '¿Cómo querés recibir tu pedido?',
          style: Theme.of(context).textTheme.titleMedium,
        ),
        const SizedBox(height: 12),
        _ChoiceCard(
          selected: !shipping,
          icon: Icons.storefront_outlined,
          title: 'Retiro en la tienda',
          subtitle: 'Lo buscás vos, sin costo de envío',
          onTap: () => onDeliveryChanged(CheckoutDelivery.pickup),
        ),
        const SizedBox(height: 8),
        _ChoiceCard(
          selected: shipping,
          icon: Icons.local_shipping_outlined,
          title: 'Envío a domicilio',
          subtitle: 'Te lo llevamos. Se paga antes, por transferencia',
          onTap: () => onDeliveryChanged(CheckoutDelivery.shipping),
        ),
        if (shipping && sinTransferencia) ...[
          const SizedBox(height: 16),
          const _Notice(
            error: true,
            message:
                'Por ahora no podemos enviar a domicilio: los envíos se pagan '
                'por transferencia y la tienda todavía no habilitó sus datos '
                'bancarios. Podés retirarlo en la tienda.',
          ),
        ],
        if (shipping && !sinTransferencia) ...[
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
              action: TextButton.icon(
                onPressed: () => ref.invalidate(addressesProvider),
                icon: const Icon(Icons.refresh),
                label: const Text('Reintentar'),
              ),
            ),
            data: (items) {
              if (items.isEmpty) {
                return _Notice(
                  message: 'Todavía no cargaste ninguna dirección.',
                  action: FilledButton.tonalIcon(
                    onPressed: () async {
                      await context.push('/addresses');
                      ref.invalidate(addressesProvider);
                    },
                    icon: const Icon(Icons.add_location_alt_outlined),
                    label: const Text('Agregar dirección'),
                  ),
                );
              }
              final elegida = items
                  .where((a) => a.id == draft.addressId)
                  .firstOrNull;
              return Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  DropdownButtonFormField<int>(
                    initialValue: elegida?.id,
                    isExpanded: true,
                    decoration: const InputDecoration(
                      labelText: 'Dirección de entrega',
                      helperText: 'Elegí dónde querés recibir el pedido',
                    ),
                    items: [
                      for (final address in items)
                        DropdownMenuItem(
                          value: address.id,
                          // El nombre que le puso la persona va primero: en
                          // la lista «Casa» se distingue de un vistazo y dos
                          // calles parecidas no.
                          child: Text(
                            '${address.label} · ${address.calleYCiudad}',
                            overflow: TextOverflow.ellipsis,
                          ),
                        ),
                    ],
                    onChanged: onAddressChanged,
                  ),
                  if (elegida != null) ...[
                    const SizedBox(height: 10),
                    _AddressSummary(
                      address: elegida,
                      onEdit: () async {
                        await context.push('/addresses');
                        ref.invalidate(addressesProvider);
                      },
                    ),
                  ],
                ],
              );
            },
          ),
          if (draft.addressId != null) ...[
            const SizedBox(height: 12),
            if (draft.quote == null)
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
                  title: Text(draft.quote!.name),
                  subtitle: Text(_dateRange(draft.quote!)),
                  trailing: Text(
                    formatMoney(draft.quote!.cost, draft.quote!.currency),
                  ),
                  onTap: onPickQuote,
                ),
              ),
          ],
        ],
      ],
    );
  }
}

/// Quién recibe y cómo llegar: lo que el repartidor va a leer en la puerta.
class _AddressSummary extends StatelessWidget {
  const _AddressSummary({required this.address, required this.onEdit});

  final DeliveryAddress address;
  final VoidCallback onEdit;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final secundario = theme.textTheme.bodySmall?.copyWith(
      color: theme.colorScheme.onSurfaceVariant,
    );
    return Card(
      margin: EdgeInsets.zero,
      child: Padding(
        padding: const EdgeInsets.fromLTRB(14, 12, 6, 12),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Padding(
              padding: EdgeInsets.only(top: 2),
              child: Icon(Icons.place_outlined, size: 20),
            ),
            const SizedBox(width: 10),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(address.calleYCiudad, style: theme.textTheme.bodyMedium),
                  const SizedBox(height: 4),
                  Text(
                    'Recibe ${address.recipient} · ${address.phone}',
                    style: secundario,
                  ),
                  if (address.reference != null &&
                      address.reference!.trim().isNotEmpty)
                    Text(
                      'Referencia: ${address.reference!.trim()}',
                      style: secundario,
                    ),
                ],
              ),
            ),
            TextButton(onPressed: onEdit, child: const Text('Editar')),
          ],
        ),
      ),
    );
  }
}

class _PaymentStep extends ConsumerWidget {
  const _PaymentStep({
    required this.draft,
    required this.transfer,
    required this.amount,
    required this.currency,
    required this.notes,
    required this.onChanged,
    required this.onNotesChanged,
    required this.onAddReceipt,
    required this.onReplaceReceipt,
    required this.onRemoveReceipt,
    required this.onRetryReceipt,
  });

  final CheckoutDraft draft;
  final AsyncValue<TransferDetails> transfer;
  final double amount;
  final String currency;
  final TextEditingController notes;
  final ValueChanged<CheckoutPayment> onChanged;
  final ValueChanged<String> onNotesChanged;
  final VoidCallback onAddReceipt;
  final ValueChanged<DraftReceipt> onReplaceReceipt;
  final ValueChanged<DraftReceipt> onRemoveReceipt;
  final ValueChanged<DraftReceipt> onRetryReceipt;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final theme = Theme.of(context);
    final options = [
      for (final option in CheckoutPayment.values)
        if (option.availableFor(draft.delivery)) option,
    ];
    final porTransferencia = draft.payment == CheckoutPayment.bankTransfer;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text('¿Cómo querés pagar?', style: theme.textTheme.titleMedium),
        if (draft.delivery == CheckoutDelivery.shipping) ...[
          const SizedBox(height: 6),
          Text(
            'Los envíos a domicilio se pagan por adelantado. Preparamos tu '
            'pedido apenas verificamos la transferencia.',
            style: theme.textTheme.bodySmall?.copyWith(
              color: theme.colorScheme.onSurfaceVariant,
            ),
          ),
        ],
        const SizedBox(height: 12),
        if (options.length > 1)
          for (final option in options) ...[
            _ChoiceCard(
              selected: option == draft.payment,
              icon: switch (option) {
                CheckoutPayment.atStore => Icons.storefront_outlined,
                CheckoutPayment.bankTransfer => Icons.account_balance_outlined,
              },
              title: switch (option) {
                CheckoutPayment.atStore => 'Pago al retirar',
                CheckoutPayment.bankTransfer => 'Transferencia bancaria',
              },
              subtitle: switch (option) {
                CheckoutPayment.atStore =>
                  'Abonás en la tienda cuando pasás a buscarlo',
                CheckoutPayment.bankTransfer =>
                  'Transferís ahora y adjuntás el comprobante',
              },
              onTap: () => onChanged(option),
            ),
            const SizedBox(height: 8),
          ],
        if (porTransferencia) ...[
          const SizedBox(height: 8),
          transfer.when(
            loading: () => const _CargandoDatos(),
            error: (_, _) => _Notice(
              error: true,
              message:
                  'No pudimos traer los datos para transferir. Revisá tu '
                  'conexión e intentá de nuevo.',
              action: TextButton.icon(
                onPressed: () => ref.invalidate(transferDetailsProvider),
                icon: const Icon(Icons.refresh),
                label: const Text('Reintentar'),
              ),
            ),
            data: (details) => !details.available
                ? const _Notice(
                    error: true,
                    message:
                        'La tienda todavía no habilitó sus datos para '
                        'transferir. Elegí pagar al retirar.',
                  )
                : Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      TransferInstructions(
                        details: details,
                        amount: amount,
                        currency: currency,
                      ),
                      const SizedBox(height: 20),
                      _ReceiptsSection(
                        receipts: draft.receipts,
                        onAdd: onAddReceipt,
                        onReplace: onReplaceReceipt,
                        onRemove: onRemoveReceipt,
                        onRetry: onRetryReceipt,
                      ),
                    ],
                  ),
          ),
        ],
        const SizedBox(height: 20),
        TextField(
          controller: notes,
          maxLength: 1000,
          maxLines: 3,
          onChanged: onNotesChanged,
          decoration: const InputDecoration(
            labelText: 'Aclaraciones para el pedido',
            helperText: 'Opcional. Por ejemplo, un horario para recibirlo.',
          ),
        ),
      ],
    );
  }
}

class _CargandoDatos extends StatelessWidget {
  const _CargandoDatos();

  @override
  Widget build(BuildContext context) => Container(
    height: 220,
    decoration: BoxDecoration(
      color: Paleta.superficie,
      borderRadius: Medidas.bordeTarjeta,
      border: Border.all(color: Paleta.borde),
    ),
    alignment: Alignment.center,
    child: const Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        CircularProgressIndicator(),
        SizedBox(height: 12),
        Text('Trayendo los datos para transferir'),
      ],
    ),
  );
}

/// Los comprobantes de esta compra: se suben apenas se eligen, se ven antes
/// de confirmar y se pueden cambiar.
class _ReceiptsSection extends StatelessWidget {
  const _ReceiptsSection({
    required this.receipts,
    required this.onAdd,
    required this.onReplace,
    required this.onRemove,
    required this.onRetry,
  });

  final List<DraftReceipt> receipts;
  final VoidCallback onAdd;
  final ValueChanged<DraftReceipt> onReplace;
  final ValueChanged<DraftReceipt> onRemove;
  final ValueChanged<DraftReceipt> onRetry;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final lleno = receipts.length >= _maximoComprobantes;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Row(
          children: [
            Expanded(
              child: Text(
                'Comprobante de la transferencia',
                style: theme.textTheme.titleSmall,
              ),
            ),
            Text(
              '${receipts.length}/$_maximoComprobantes',
              style: theme.textTheme.labelMedium?.copyWith(
                color: theme.colorScheme.onSurfaceVariant,
              ),
            ),
          ],
        ),
        const SizedBox(height: 4),
        Text(
          receipts.isEmpty
              ? 'Cuando hayas transferido, adjuntá la captura o el PDF del '
                    'comprobante. Sin él no podemos confirmar tu compra.'
              : 'Revisá que se lea el monto y la fecha. Tocá la miniatura '
                    'para verla entera.',
          style: theme.textTheme.bodySmall?.copyWith(
            color: theme.colorScheme.onSurfaceVariant,
          ),
        ),
        const SizedBox(height: 12),
        for (final receipt in receipts) ...[
          _DraftReceiptTile(
            receipt: receipt,
            onReplace: () => onReplace(receipt),
            onRemove: () => onRemove(receipt),
            onRetry: () => onRetry(receipt),
          ),
          const SizedBox(height: 10),
        ],
        if (!lleno)
          receipts.isEmpty
              ? FilledButton.icon(
                  onPressed: onAdd,
                  style: FilledButton.styleFrom(
                    minimumSize: const Size.fromHeight(52),
                  ),
                  icon: const Icon(Icons.upload_file_outlined),
                  label: const Text('Adjuntar comprobante'),
                )
              : OutlinedButton.icon(
                  onPressed: onAdd,
                  icon: const Icon(Icons.add),
                  label: const Text('Adjuntar otro'),
                ),
      ],
    );
  }
}

class _DraftReceiptTile extends StatelessWidget {
  const _DraftReceiptTile({
    required this.receipt,
    required this.onReplace,
    required this.onRemove,
    required this.onRetry,
  });

  final DraftReceipt receipt;
  final VoidCallback onReplace;
  final VoidCallback onRemove;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final error = receipt.error;
    final (estado, color) = error != null
        ? (error, theme.colorScheme.error)
        : receipt.uploading
        ? (
            'Subiendo… ${(receipt.progress * 100).clamp(0, 99).round()} %',
            theme.colorScheme.onSurfaceVariant,
          )
        : ('Listo para enviar', Paleta.exito);
    return Container(
      padding: const EdgeInsets.all(10),
      decoration: BoxDecoration(
        color: Paleta.superficie,
        borderRadius: BorderRadius.circular(Medidas.radioControl),
        border: Border.all(
          color: error != null ? theme.colorScheme.error : Paleta.borde,
        ),
      ),
      child: Row(
        children: [
          ReceiptThumbnail(
            isPdf: receipt.picked.isPdf,
            bytes: receipt.picked.bytes,
            size: 72,
            progress: receipt.uploading ? receipt.progress : null,
            onTap: receipt.picked.isPdf
                ? null
                : () => showReceiptImage(context, receipt.picked.bytes),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  receipt.picked.isPdf
                      ? 'Comprobante en PDF'
                      : 'Foto del comprobante',
                  style: theme.textTheme.bodyMedium,
                ),
                const SizedBox(height: 2),
                Row(
                  children: [
                    if (error == null && !receipt.uploading)
                      const Padding(
                        padding: EdgeInsets.only(right: 4),
                        child: Icon(
                          Icons.check_circle,
                          size: 14,
                          color: Paleta.exito,
                        ),
                      ),
                    Flexible(
                      child: Text(
                        estado,
                        maxLines: 2,
                        overflow: TextOverflow.ellipsis,
                        style: theme.textTheme.bodySmall?.copyWith(
                          color: color,
                        ),
                      ),
                    ),
                  ],
                ),
                if (error != null)
                  TextButton.icon(
                    onPressed: onRetry,
                    icon: const Icon(Icons.refresh, size: 18),
                    label: const Text('Reintentar'),
                  ),
              ],
            ),
          ),
          PopupMenuButton<String>(
            tooltip: 'Opciones del comprobante',
            enabled: !receipt.uploading,
            onSelected: (opcion) =>
                opcion == 'cambiar' ? onReplace() : onRemove(),
            itemBuilder: (context) => const [
              PopupMenuItem(value: 'cambiar', child: Text('Reemplazar')),
              PopupMenuItem(value: 'quitar', child: Text('Quitar')),
            ],
          ),
        ],
      ),
    );
  }
}

class _ConfirmStep extends StatelessWidget {
  const _ConfirmStep({
    required this.cart,
    required this.draft,
    required this.total,
    required this.currency,
  });

  final List<CartLine> cart;
  final CheckoutDraft draft;
  final double total;
  final String currency;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final subtotals = totalsByCurrency([
      for (final line in cart)
        (amount: line.subtotal, currency: line.product.currency),
    ]);
    final quote = draft.quote;
    final shipping = draft.delivery == CheckoutDelivery.shipping;
    final porTransferencia = draft.payment == CheckoutPayment.bankTransfer;
    final comprobantes = draft.uploadedReceipts.length;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text('Revisá tu pedido', style: theme.textTheme.titleMedium),
        const SizedBox(height: 12),
        Card(
          margin: EdgeInsets.zero,
          child: Padding(
            padding: const EdgeInsets.all(14),
            child: Column(
              children: [
                for (final line in cart)
                  Padding(
                    padding: const EdgeInsets.symmetric(vertical: 4),
                    child: Row(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Expanded(
                          child: Text(
                            '${line.quantity} × ${line.product.name}',
                            style: theme.textTheme.bodyMedium,
                          ),
                        ),
                        const SizedBox(width: 12),
                        Text(
                          formatMoney(line.subtotal, line.product.currency),
                          textAlign: TextAlign.end,
                          style: theme.textTheme.bodyMedium,
                        ),
                      ],
                    ),
                  ),
                const Divider(height: 20),
                _SummaryRow(label: 'Productos', value: formatTotals(subtotals)),
                _SummaryRow(
                  label: 'Envío',
                  value: !shipping
                      ? 'Sin costo (retirás vos)'
                      : quote == null
                      ? 'A confirmar'
                      : formatMoney(quote.cost, quote.currency),
                ),
                const Divider(height: 20),
                _SummaryRow(
                  label: 'Total',
                  value: formatMoney(total, currency),
                  emphasize: true,
                ),
              ],
            ),
          ),
        ),
        const SizedBox(height: 16),
        _DetailTile(
          icon: shipping
              ? Icons.local_shipping_outlined
              : Icons.storefront_outlined,
          title: shipping ? 'Envío a domicilio' : 'Retiro en la tienda',
          subtitle: shipping && quote != null
              ? '${quote.name} · ${_dateRange(quote)}'
              : null,
        ),
        _DetailTile(
          icon: porTransferencia
              ? Icons.account_balance_outlined
              : Icons.storefront_outlined,
          title: porTransferencia
              ? 'Transferencia bancaria'
              : 'Pago al retirar',
          subtitle: porTransferencia
              ? (comprobantes == 1
                    ? '1 comprobante adjunto'
                    : '$comprobantes comprobantes adjuntos')
              : null,
        ),
        if (draft.notes.trim().isNotEmpty)
          _DetailTile(
            icon: Icons.sticky_note_2_outlined,
            title: 'Tus aclaraciones',
            subtitle: draft.notes.trim(),
          ),
        if (porTransferencia) ...[
          const SizedBox(height: 8),
          const _Notice(
            message:
                'Al confirmar, tu pedido queda con el pago en verificación. '
                'Lo preparamos apenas confirmemos que la transferencia llegó, '
                'y te avisamos por notificación.',
          ),
        ],
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
        ? theme.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w800)
        : theme.textTheme.bodyMedium?.copyWith(
            color: theme.colorScheme.onSurfaceVariant,
          );
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 3),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Expanded(child: Text(label, style: style)),
          const SizedBox(width: 12),
          Flexible(
            flex: 2,
            child: Text(value, style: style, textAlign: TextAlign.end),
          ),
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
    return Semantics(
      selected: selected,
      button: true,
      child: AnimatedContainer(
        duration: Movimiento.o(context, Movimiento.corto),
        decoration: BoxDecoration(
          color: selected
              ? theme.colorScheme.primaryContainer
              : Paleta.superficie,
          borderRadius: BorderRadius.circular(Medidas.radioControl),
          // La selección no se comunica solo con color: hay borde marcado y una
          // tilde, para que se distinga sin depender de la vista del color.
          border: Border.all(
            color: selected
                ? theme.colorScheme.primary
                : theme.colorScheme.outlineVariant,
            width: selected ? 2 : 1,
          ),
        ),
        child: InkWell(
          borderRadius: BorderRadius.circular(Medidas.radioControl),
          onTap: onTap,
          child: Padding(
            padding: const EdgeInsets.all(14),
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
                AnimatedOpacity(
                  opacity: selected ? 1 : 0,
                  duration: Movimiento.o(context, Movimiento.corto),
                  child: Icon(
                    Icons.check_circle,
                    color: theme.colorScheme.primary,
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _Notice extends StatelessWidget {
  const _Notice({required this.message, this.action, this.error = false});

  final String message;
  final Widget? action;
  final bool error;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final fondo = error
        ? theme.colorScheme.errorContainer
        : theme.colorScheme.secondaryContainer;
    final texto = error
        ? theme.colorScheme.onErrorContainer
        : theme.colorScheme.onSecondaryContainer;
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: fondo,
        borderRadius: BorderRadius.circular(12),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Icon(
                error ? Icons.error_outline : Icons.info_outline,
                size: 20,
                color: texto,
              ),
              const SizedBox(width: 8),
              Expanded(
                child: Text(
                  message,
                  style: theme.textTheme.bodyMedium?.copyWith(color: texto),
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
        padding: const EdgeInsets.fromLTRB(16, 8, 16, 16),
        child: LayoutBuilder(
          builder: (context, constraints) {
            // El botón principal ocupa el ancho entero y «Confirmar compra»
            // entra en una línea: antes los dos botones se repartían el ancho
            // y el texto se partía en dos renglones.
            final volver = TextButton(
              onPressed: onBack,
              child: Text(
                step == 0 ? 'Volver al carrito' : 'Atrás',
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
              ),
            );
            final continuar = FilledButton.icon(
              // Mientras procesa queda deshabilitado: evita que dos toques
              // generen dos pedidos.
              onPressed: busy || !canContinue ? null : onNext,
              style: FilledButton.styleFrom(
                minimumSize: const Size.fromHeight(52),
              ),
              icon: busy
                  ? const SizedBox.square(
                      dimension: 18,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    )
                  : Icon(
                      isLast ? Icons.check_circle_outline : Icons.arrow_forward,
                    ),
              label: Text(
                busy
                    ? 'Procesando pedido…'
                    : isLast
                    ? 'Confirmar compra'
                    : 'Continuar',
                maxLines: 1,
              ),
            );
            return Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [continuar, const SizedBox(height: 4), volver],
            );
          },
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
                onPressed: () => context.push('/catalog'),
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
