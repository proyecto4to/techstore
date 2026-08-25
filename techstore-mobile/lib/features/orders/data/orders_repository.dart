import '../../../core/network/api_client.dart';
import '../domain/order.dart';

final class OrdersRepository {
  const OrdersRepository(this._api);

  final ApiClient _api;

  Future<List<OrderSummary>> list() async {
    final response = await _api.get<Map<String, Object?>>(
      '/api/v1/pedidos',
      queryParameters: {'page': 0, 'size': 30, 'sort': 'fecha,DESC'},
    );
    return (response.data?['content'] as List? ?? const [])
        .map(
          (item) =>
              OrderSummary.fromJson(Map<String, Object?>.from(item as Map)),
        )
        .toList(growable: false);
  }

  Future<Map<String, Object?>> get(int id) async {
    final response = await _api.get<Map<String, Object?>>(
      '/api/v1/pedidos/$id',
    );
    return response.data!;
  }

  Future<void> cancel(int id) => _api.put<void>('/api/v1/pedidos/$id/cancelar');

  Future<Map<String, Object?>> shipment(int id) async {
    final response = await _api.get<Map<String, Object?>>(
      '/api/v1/envios/pedidos/$id',
    );
    return response.data!;
  }
}
