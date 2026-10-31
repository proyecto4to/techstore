import 'dart:typed_data';

import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/di/providers.dart';
import '../../../core/errors/app_failure.dart';
import '../../../core/native/receipt_picker.dart';
import '../data/payments_repository.dart';
import '../domain/payment_models.dart';

/// Ninguno de estos reintenta solo: cada pantalla que los usa ofrece
/// «Reintentar», y un reintento automático por detrás volvía a pedir los
/// datos del banco sin que nadie lo pidiera, una y otra vez, con la red caída.
Duration? _sinReintento(int intento, Object error) => null;

final paymentsRepositoryProvider = Provider<PaymentsRepository>(
  (ref) => PaymentsRepository(ref.watch(apiClientProvider)),
);

final receiptPickerProvider = Provider<ReceiptPicker>(
  (ref) => ReceiptPicker(ref.watch(appExcursionProvider)),
);

/// Los datos para transferir. Se piden cada vez que se abre el pago: si la
/// tienda cambió de cuenta, nadie puede transferir a la vieja por un caché.
final transferDetailsProvider = FutureProvider.autoDispose<TransferDetails>(
  (ref) => ref.watch(paymentsRepositoryProvider).transferDetails(),
  retry: _sinReintento,
);

final transferQrProvider = FutureProvider.autoDispose<Uint8List>(
  (ref) => ref.watch(paymentsRepositoryProvider).transferQr(),
  retry: _sinReintento,
);

final orderReceiptsProvider = FutureProvider.autoDispose
    .family<List<PaymentReceipt>, int>(
      (ref, orderId) =>
          ref.watch(paymentsRepositoryProvider).orderReceipts(orderId),
      retry: _sinReintento,
    );

/// La imagen de un comprobante, por pedido y por id.
final orderReceiptFileProvider = FutureProvider.autoDispose
    .family<Uint8List, (int, int)>(
      (ref, ids) => ref
          .watch(paymentsRepositoryProvider)
          .orderReceiptFile(ids.$1, ids.$2),
      retry: _sinReintento,
    );

/// El historial es información de más: si no se puede leer, el pedido se ve
/// igual, sin la sección de actividad.
final orderHistoryProvider = FutureProvider.autoDispose
    .family<List<OrderEvent>, int>((ref, orderId) async {
      try {
        return await ref
            .watch(paymentsRepositoryProvider)
            .orderHistory(orderId);
      } on AppFailure {
        return const [];
      }
    }, retry: _sinReintento);

/// La bandeja de quien cobra. [all] suma los que esperan un comprobante.
final pendingPaymentsProvider = FutureProvider.autoDispose
    .family<List<PendingPayment>, bool>(
      (ref, all) =>
          ref.watch(paymentsRepositoryProvider).pendingPayments(all: all),
      retry: _sinReintento,
    );

/// Cuántos pagos esperan revisión, para el número de la baldosa. Sin
/// respuesta, cero: la baldosa sigue sirviendo para entrar.
final pendingPaymentsCountProvider = FutureProvider.autoDispose<int>((
  ref,
) async {
  try {
    return await ref.watch(paymentsRepositoryProvider).pendingCount();
  } on AppFailure {
    return 0;
  }
}, retry: _sinReintento);
