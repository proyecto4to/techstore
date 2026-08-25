import 'dart:convert';

import '../../../core/cache/cache_store.dart';
import '../../../core/errors/app_failure.dart';
import '../../../core/network/api_client.dart';
import '../domain/product.dart';

final class CatalogRepository {
  const CatalogRepository(
    this._api, [
    this._cache,
    this._scope = 'public:catalog',
  ]);

  final ApiClient _api;
  final CacheStore? _cache;

  final String _scope;
  static const _ttl = Duration(minutes: 15);

  Future<List<Product>> list({
    String search = '',
    int? brandId,
    int page = 0,
  }) async {
    final key = 'products:$page:${brandId ?? ''}:${search.trim()}';
    final content = await _cachedList(key, () async {
      final response = await _api.get<Map<String, Object?>>(
        '/api/v1/productos',
        queryParameters: {
          'page': page,
          'size': 30,
          if (search.trim().isNotEmpty) 'search': search.trim(),
          'marcaId': ?brandId,
        },
      );
      return response.data?['content'] as List? ?? const [];
    });
    return content
        .map((item) => Product.fromJson(Map<String, Object?>.from(item as Map)))
        .toList(growable: false);
  }

  Future<List<Map<String, Object?>>> brands() async {
    final content = await _cachedList('brands', () async {
      final response = await _api.get<List<dynamic>>('/api/v1/marcas');
      return response.data ?? const [];
    });
    return content
        .map((item) => Map<String, Object?>.from(item as Map))
        .toList(growable: false);
  }

  Future<Product> get(int id) async {
    final key = 'product:$id';
    try {
      final response = await _api.get<Map<String, Object?>>(
        '/api/v1/productos/$id',
      );
      final data = response.data!;
      await _cache?.write(
        scope: _scope,
        key: key,
        json: jsonEncode(data),
        ttl: _ttl,
      );
      return Product.fromJson(data);
    } on NetworkFailure {
      final cached = await _cache?.readStale(scope: _scope, key: key);
      if (cached == null) rethrow;
      return Product.fromJson(
        Map<String, Object?>.from(jsonDecode(cached) as Map),
      );
    }
  }

  Future<List<dynamic>> _cachedList(
    String key,
    Future<List<dynamic>> Function() request,
  ) async {
    try {
      final value = await request();
      await _cache?.write(
        scope: _scope,
        key: key,
        json: jsonEncode(value),
        ttl: _ttl,
      );
      return value;
    } on NetworkFailure {
      final cached = await _cache?.readStale(scope: _scope, key: key);
      if (cached == null) rethrow;
      return jsonDecode(cached) as List<dynamic>;
    }
  }
}
