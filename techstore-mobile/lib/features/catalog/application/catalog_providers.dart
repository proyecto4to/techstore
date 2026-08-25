import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/di/providers.dart';
import '../data/catalog_repository.dart';
import '../data/favorites_repository.dart';
import '../domain/product.dart';

final catalogRepositoryProvider = Provider<CatalogRepository>(
  (ref) => CatalogRepository(
    ref.watch(apiClientProvider),
    ref.watch(cacheStoreProvider),
    'catalog:${ref.watch(environmentProvider).apiBaseUrl}',
  ),
);

final catalogProvider = FutureProvider.autoDispose
    .family<List<Product>, ({String search, int? brandId})>(
      (ref, query) => ref
          .watch(catalogRepositoryProvider)
          .list(search: query.search, brandId: query.brandId),
    );

final brandsProvider = FutureProvider.autoDispose(
  (ref) => ref.watch(catalogRepositoryProvider).brands(),
);

final productProvider = FutureProvider.autoDispose.family<Product, int>(
  (ref, id) => ref.watch(catalogRepositoryProvider).get(id),
);

final favoritesRepositoryProvider = Provider<FavoritesRepository>(
  (ref) => FavoritesRepository(ref.watch(apiClientProvider)),
);

final favoritesProvider = FutureProvider.autoDispose<List<Product>>(
  (ref) => ref.watch(favoritesRepositoryProvider).list(),
);
