import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:uuid/uuid.dart';

import '../../../core/native/receipt_picker.dart';
import '../../auth/application/session_controller.dart';
import '../../payments/domain/payment_models.dart';
import '../domain/checkout_models.dart';

/// Cómo se entrega el pedido.
enum CheckoutDelivery { pickup, shipping }

/// Formas de pago, con el código que espera el servidor.
enum CheckoutPayment {
  atStore('PAGO_EN_LOCAL'),
  bankTransfer('TRANSFERENCIA_BANCARIA');

  const CheckoutPayment(this.code);

  final String code;

  /// Un envío a domicilio se paga antes, por transferencia: es la regla del
  /// negocio y la valida también el servidor. Pagar en el local solo existe si
  /// se retira.
  bool availableFor(CheckoutDelivery delivery) => switch (this) {
    CheckoutPayment.atStore => delivery == CheckoutDelivery.pickup,
    CheckoutPayment.bankTransfer => true,
  };
}

/// Un comprobante elegido en el checkout: mientras sube, ya subido o con error.
final class DraftReceipt {
  const DraftReceipt({
    required this.localId,
    required this.picked,
    this.uploaded,
    this.progress = 0,
    this.error,
  });

  /// Para identificarlo en la lista antes de que el servidor le dé un id.
  final String localId;
  final PickedReceipt picked;
  final PaymentReceipt? uploaded;
  final double progress;
  final String? error;

  bool get uploading => uploaded == null && error == null;

  DraftReceipt copyWith({
    PaymentReceipt? uploaded,
    double? progress,
    String? error,
    bool clearError = false,
  }) => DraftReceipt(
    localId: localId,
    picked: picked,
    uploaded: uploaded ?? this.uploaded,
    progress: progress ?? this.progress,
    error: clearError ? null : (error ?? this.error),
  );
}

/// Lo que lleva hecho la persona en el checkout.
///
/// Vive fuera de la pantalla a propósito: si sale a mirar otro producto o a
/// cargar una dirección y vuelve, encuentra el pago y los comprobantes como los
/// dejó. Antes, salir del checkout era empezarlo de nuevo, y un comprobante ya
/// subido había que volver a buscarlo en la galería.
final class CheckoutDraft {
  const CheckoutDraft({
    this.step = 0,
    this.delivery = CheckoutDelivery.pickup,
    this.addressId,
    this.quote,
    this.payment = CheckoutPayment.atStore,
    this.notes = '',
    this.receipts = const [],
    this.idempotencyKey,
  });

  /// 0 entrega, 1 pago, 2 confirmación.
  final int step;
  final CheckoutDelivery delivery;
  final int? addressId;
  final ShippingQuote? quote;
  final CheckoutPayment payment;
  final String notes;
  final List<DraftReceipt> receipts;

  /// La misma clave en cada reintento de la misma compra: si se cortó la red
  /// después de que el servidor creó el pedido, reintentar devuelve ese pedido
  /// en vez de crear otro. Se renueva cuando cambia algo de lo que se compra.
  final String? idempotencyKey;

  bool get deliveryReady =>
      delivery == CheckoutDelivery.pickup ||
      (addressId != null && quote != null);

  List<PaymentReceipt> get uploadedReceipts => [
    for (final receipt in receipts)
      if (receipt.uploaded != null) receipt.uploaded!,
  ];

  bool get uploadingReceipts => receipts.any((r) => r.uploading);

  /// Si el pago está listo para pasar a confirmar.
  bool get paymentReady =>
      payment != CheckoutPayment.bankTransfer ||
      (uploadedReceipts.isNotEmpty && !uploadingReceipts);

  CheckoutDraft copyWith({
    int? step,
    CheckoutDelivery? delivery,
    int? addressId,
    bool clearAddress = false,
    ShippingQuote? quote,
    bool clearQuote = false,
    CheckoutPayment? payment,
    String? notes,
    List<DraftReceipt>? receipts,
    String? idempotencyKey,
    bool clearKey = false,
  }) => CheckoutDraft(
    step: step ?? this.step,
    delivery: delivery ?? this.delivery,
    addressId: clearAddress ? null : (addressId ?? this.addressId),
    quote: clearQuote ? null : (quote ?? this.quote),
    payment: payment ?? this.payment,
    notes: notes ?? this.notes,
    receipts: receipts ?? this.receipts,
    idempotencyKey: clearKey ? null : (idempotencyKey ?? this.idempotencyKey),
  );
}

final checkoutDraftProvider =
    NotifierProvider<CheckoutDraftController, CheckoutDraft>(
      CheckoutDraftController.new,
    );

final class CheckoutDraftController extends Notifier<CheckoutDraft> {
  @override
  CheckoutDraft build() {
    // Otra cuenta en el mismo teléfono empieza de cero: el borrador de quien
    // salió no puede quedarle a quien entra, y menos sus comprobantes.
    ref.watch(authSessionProvider.select((s) => s.user?.id));
    return const CheckoutDraft();
  }

  void goTo(int step) => state = state.copyWith(step: step);

  void setDelivery(CheckoutDelivery delivery) {
    final payment = state.payment.availableFor(delivery)
        ? state.payment
        : CheckoutPayment.bankTransfer;
    state = state.copyWith(
      delivery: delivery,
      payment: payment,
      clearAddress: delivery == CheckoutDelivery.pickup,
      clearQuote: true,
      clearKey: true,
    );
  }

  void setAddress(int? id) => state = id == null
      ? state.copyWith(clearAddress: true, clearQuote: true, clearKey: true)
      : state.copyWith(addressId: id, clearQuote: true, clearKey: true);

  void setQuote(ShippingQuote quote) =>
      state = state.copyWith(quote: quote, clearKey: true);

  void setPayment(CheckoutPayment payment) =>
      state = state.copyWith(payment: payment, clearKey: true);

  void setNotes(String notes) =>
      state = state.copyWith(notes: notes, clearKey: true);

  /// La clave de esta compra; la crea si todavía no hay una.
  String ensureKey() {
    final actual = state.idempotencyKey;
    if (actual != null) return actual;
    final nueva = const Uuid().v4();
    state = state.copyWith(idempotencyKey: nueva);
    return nueva;
  }

  /// Si cambió el carrito, también es otra compra.
  void invalidateKey() => state = state.copyWith(clearKey: true);

  void addReceipt(DraftReceipt receipt) => state = state.copyWith(
    receipts: [...state.receipts, receipt],
    clearKey: true,
  );

  void updateReceipt(
    String localId,
    DraftReceipt Function(DraftReceipt) update,
  ) => state = state.copyWith(
    receipts: [
      for (final receipt in state.receipts)
        receipt.localId == localId ? update(receipt) : receipt,
    ],
  );

  void removeReceipt(String localId) => state = state.copyWith(
    receipts: [
      for (final receipt in state.receipts)
        if (receipt.localId != localId) receipt,
    ],
    clearKey: true,
  );

  /// La compra terminó: lo próximo empieza de cero.
  void reset() => state = const CheckoutDraft();
}
