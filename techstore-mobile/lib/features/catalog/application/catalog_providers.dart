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

/// Cuántos productos trae cada bloque. Tiene que coincidir con el `size` que
/// pide el repositorio: es cómo se sabe si quedan más.
const int productosPorBloque = 30;

typedef ConsultaDeCatalogo = ({String search, int? brandId});

/// El catálogo, cargado de a bloques.
///
/// Antes traía una sola página de treinta y no había forma de ver el resto: el
/// repositorio aceptaba `page` pero nadie se lo pasaba. Ahora acumula lo que ya
/// se mostró y pide el siguiente bloque al llegar al final de la grilla, para
/// no bajar cientos de fichas con sus imágenes de una sola vez.
final catalogProvider =
    AsyncNotifierProvider.autoDispose
        .family<CatalogoPaginado, List<Product>, ConsultaDeCatalogo>(
          CatalogoPaginado.new,
        );

class CatalogoPaginado extends AsyncNotifier<List<Product>> {
  CatalogoPaginado(this.consulta);

  /// Qué se está mirando. En Riverpod 3 el argumento de la familia llega por el
  /// constructor, no por el `build`.
  final ConsultaDeCatalogo consulta;

  int _pagina = 0;
  bool _hayMas = true;
  bool _cargando = false;

  /// Si puede quedar algo más por traer.
  bool get hayMas => _hayMas;

  @override
  Future<List<Product>> build() async {
    _pagina = 0;
    _cargando = false;
    final primeros = await ref
        .watch(catalogRepositoryProvider)
        .list(search: consulta.search, brandId: consulta.brandId);
    // Un bloque incompleto ya es el último: no hace falta una consulta extra
    // para descubrir que no hay nada más.
    _hayMas = primeros.length >= productosPorBloque;
    return primeros;
  }

  /// Suma el siguiente bloque a lo que ya está en pantalla.
  ///
  /// No hace nada si ya está trayendo uno o si no quedan más: la grilla puede
  /// llamarlo varias veces mientras el usuario sigue bajando.
  Future<void> cargarMas() async {
    if (_cargando || !_hayMas) return;
    final actuales = state.value;
    if (actuales == null) return;
    _cargando = true;
    try {
      final siguientes = await ref
          .read(catalogRepositoryProvider)
          .list(
            search: consulta.search,
            brandId: consulta.brandId,
            page: _pagina + 1,
          );
      _pagina += 1;
      _hayMas = siguientes.length >= productosPorBloque;
      state = AsyncData([...actuales, ...siguientes]);
    } on Object {
      // Que falle un bloque no tira lo que ya se mostró: se deja de pedir más
      // y el usuario conserva lo que estaba viendo.
      _hayMas = false;
      state = AsyncData(actuales);
    } finally {
      _cargando = false;
    }
  }
}

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
