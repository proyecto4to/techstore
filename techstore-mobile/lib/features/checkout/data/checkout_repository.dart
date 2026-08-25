import 'package:dio/dio.dart';
import 'package:uuid/uuid.dart';

import '../../../core/network/api_client.dart';
import '../../cart/application/cart_controller.dart';
import '../domain/checkout_models.dart';

final class CheckoutRepository {
  const CheckoutRepository(this._api);

  final ApiClient _api;

  List<Map<String, int>> _items(List<CartLine> lines) => [
    for (final line in lines)
      {'productoId': line.product.id, 'cantidad': line.quantity},
  ];

  Future<Map<String, Object?>> validate(List<CartLine> lines) async {
    final response = await _api.post<Map<String, Object?>>(
      '/api/v1/carrito/validar',
      data: {'items': _items(lines)},
    );
    return response.data!;
  }

  Future<List<DeliveryAddress>> addresses() async {
    final response = await _api.get<List<dynamic>>('/api/v1/direcciones');
    return (response.data ?? const [])
        .map(
          (item) =>
              DeliveryAddress.fromJson(Map<String, Object?>.from(item as Map)),
        )
        .toList(growable: false);
  }

  Future<DeliveryAddress> createAddress(Map<String, Object?> data) async {
    final response = await _api.post<Map<String, Object?>>(
      '/api/v1/direcciones',
      data: data,
    );
    return DeliveryAddress.fromJson(response.data!);
  }

  Future<DeliveryAddress> updateAddress(
    int id,
    Map<String, Object?> data,
  ) async {
    final response = await _api.put<Map<String, Object?>>(
      '/api/v1/direcciones/$id',
      data: data,
    );
    return DeliveryAddress.fromJson(response.data!);
  }

  Future<void> deleteAddress(int id) =>
      _api.delete<void>('/api/v1/direcciones/$id');

  Future<List<ShippingQuote>> quotes(
    List<CartLine> lines, {
    required int addressId,
  }) async {
    final response = await _api.post<List<dynamic>>(
      '/api/v1/envios/cotizar',
      data: {'direccionEntregaId': addressId, 'items': _items(lines)},
    );
    return (response.data ?? const [])
        .map(
          (item) =>
              ShippingQuote.fromJson(Map<String, Object?>.from(item as Map)),
        )
        .toList(growable: false);
  }

  Future<int> checkout(
    List<CartLine> lines, {
    required bool delivery,
    int? addressId,
    int? tariffId,
    required String paymentMethod,
    String? notes,
  }) async {
    final response = await _api.post<Map<String, Object?>>(
      '/api/v1/pedidos',
      options: Options(headers: {'Idempotency-Key': const Uuid().v4()}),
      data: {
        'items': _items(lines),
        'metodoEntrega': delivery ? 'ENVIO_DOMICILIO' : 'RETIRO_TIENDA',
        'metodoPago': paymentMethod,
        if (delivery) 'direccionEntregaId': addressId,
        if (delivery) 'tarifaEnvioId': tariffId,
        if (notes != null && notes.trim().isNotEmpty) 'notas': notes.trim(),
      },
    );
    return (response.data!['id'] as num).toInt();
  }
}
