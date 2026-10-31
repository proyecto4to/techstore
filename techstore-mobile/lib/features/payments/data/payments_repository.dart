import 'dart:typed_data';

import 'package:dio/dio.dart';

import '../../../core/native/receipt_picker.dart';
import '../../../core/network/api_client.dart';
import '../domain/payment_models.dart';

/// El cobro por transferencia: datos del comercio, comprobantes y verificación.
final class PaymentsRepository {
  const PaymentsRepository(this._api);

  final ApiClient _api;

  Future<TransferDetails> transferDetails() async {
    final response = await _api.get<Map<String, Object?>>(
      '/api/v1/pagos/transferencia',
    );
    return TransferDetails.fromJson(response.data ?? const {});
  }

  Future<Uint8List> transferQr() =>
      _api.getBytes('/api/v1/pagos/transferencia/qr');

  /// Sube un comprobante antes de confirmar la compra. Queda solo de quien lo
  /// subió hasta que el checkout lo use.
  Future<PaymentReceipt> uploadDraftReceipt(
    PickedReceipt receipt, {
    void Function(double progreso)? onProgress,
  }) async {
    final response = await _api.upload<Map<String, Object?>>(
      '/api/v1/pagos/comprobantes',
      _formData(receipt),
      onSendProgress: _progreso(onProgress),
    );
    return PaymentReceipt.fromJson(response.data!);
  }

  Future<void> deleteDraftReceipt(int id) =>
      _api.delete<void>('/api/v1/pagos/comprobantes/$id');

  /// Adjunta otro comprobante a un pedido que ya existe, por ejemplo después
  /// de un rechazo.
  Future<PaymentReceipt> uploadOrderReceipt(
    int orderId,
    PickedReceipt receipt, {
    void Function(double progreso)? onProgress,
  }) async {
    final response = await _api.upload<Map<String, Object?>>(
      '/api/v1/pedidos/$orderId/comprobantes',
      _formData(receipt),
      onSendProgress: _progreso(onProgress),
    );
    return PaymentReceipt.fromJson(response.data!);
  }

  Future<List<PaymentReceipt>> orderReceipts(int orderId) async {
    final response = await _api.get<List<dynamic>>(
      '/api/v1/pedidos/$orderId/comprobantes',
    );
    return [
      for (final item in response.data ?? const [])
        PaymentReceipt.fromJson(Map<String, Object?>.from(item as Map)),
    ];
  }

  Future<Uint8List> orderReceiptFile(int orderId, int receiptId) =>
      _api.getBytes('/api/v1/pedidos/$orderId/comprobantes/$receiptId/archivo');

  Future<void> deleteOrderReceipt(int orderId, int receiptId) =>
      _api.delete<void>('/api/v1/pedidos/$orderId/comprobantes/$receiptId');

  Future<List<OrderEvent>> orderHistory(int orderId) async {
    final response = await _api.get<List<dynamic>>(
      '/api/v1/pedidos/$orderId/historial',
    );
    return [
      for (final item in response.data ?? const [])
        OrderEvent.fromJson(Map<String, Object?>.from(item as Map)),
    ];
  }

  // Lo que sigue es de quien cobra.

  Future<List<PendingPayment>> pendingPayments({bool all = false}) async {
    final response = await _api.get<List<dynamic>>(
      '/api/v1/pagos/verificaciones',
      queryParameters: {'todos': all},
    );
    return [
      for (final item in response.data ?? const [])
        PendingPayment.fromJson(Map<String, Object?>.from(item as Map)),
    ];
  }

  Future<int> pendingCount() async {
    final response = await _api.get<Map<String, Object?>>(
      '/api/v1/pagos/verificaciones/cuantos',
    );
    return (response.data?['enVerificacion'] as num?)?.toInt() ?? 0;
  }

  Future<void> approve(int orderId, {String? reference}) => _api.put<void>(
    '/api/v1/pedidos/$orderId/pago/aprobar',
    data: {
      if (reference != null && reference.trim().isNotEmpty)
        'referencia': reference.trim(),
    },
  );

  Future<void> reject(int orderId, String reason) => _api.put<void>(
    '/api/v1/pedidos/$orderId/pago/rechazar',
    data: {'motivo': reason.trim()},
  );

  Future<TransferDetails> saveTransferDetails(TransferDetails details) async {
    final response = await _api.put<Map<String, Object?>>(
      '/api/v1/pagos/transferencia',
      data: details.toJson(),
    );
    return TransferDetails.fromJson(response.data ?? const {});
  }

  Future<TransferDetails> uploadTransferQr(PickedReceipt image) async {
    final response = await _api.upload<Map<String, Object?>>(
      '/api/v1/pagos/transferencia/qr',
      _formData(image),
    );
    return TransferDetails.fromJson(response.data ?? const {});
  }

  Future<TransferDetails> deleteTransferQr() async {
    final response = await _api.delete<Map<String, Object?>>(
      '/api/v1/pagos/transferencia/qr',
    );
    return TransferDetails.fromJson(response.data ?? const {});
  }

  static FormData _formData(PickedReceipt receipt) {
    final partes = receipt.mimeType.split('/');
    return FormData.fromMap({
      'archivo': MultipartFile.fromBytes(
        receipt.bytes,
        filename: _nombre(receipt),
        contentType: DioMediaType(partes.first, partes.last),
      ),
    });
  }

  /// El servidor guarda el archivo con un nombre propio; el que viaja solo
  /// tiene que tener la extensión correcta.
  static String _nombre(PickedReceipt receipt) => switch (receipt.mimeType) {
    'application/pdf' => 'comprobante.pdf',
    'image/png' => 'comprobante.png',
    _ => 'comprobante.jpg',
  };

  static ProgressCallback? _progreso(void Function(double)? onProgress) =>
      onProgress == null
      ? null
      : (enviado, total) {
          if (total > 0) onProgress(enviado / total);
        };
}
