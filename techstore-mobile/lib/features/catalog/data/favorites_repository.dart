import '../../../core/network/api_client.dart';
import '../domain/product.dart';

final class FavoritesRepository {
  const FavoritesRepository(this._api);

  final ApiClient _api;

  Future<List<Product>> list() async {
    final response = await _api.get<List<dynamic>>('/api/v1/favoritos');
    return (response.data ?? const [])
        .map(
          (item) => Product.fromJson(
            Map<String, Object?>.from((item as Map)['producto'] as Map),
          ),
        )
        .toList(growable: false);
  }

  Future<void> add(int productId) =>
      _api.post<void>('/api/v1/favoritos/$productId');

  Future<void> remove(int productId) =>
      _api.delete<void>('/api/v1/favoritos/$productId');
}
