import 'dart:async';

import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../app/realtime_coordinator.dart';
import '../../../core/di/providers.dart';
import '../../../core/realtime/agregados_de_sincronizacion.dart';
import '../../../core/realtime/realtime_event.dart';
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
final catalogProvider = AsyncNotifierProvider.autoDispose
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
    _escucharCambios();
    final primeros = await ref
        .watch(catalogRepositoryProvider)
        .list(search: consulta.search, brandId: consulta.brandId);
    // Un bloque incompleto ya es el último: no hace falta una consulta extra
    // para descubrir que no hay nada más.
    _hayMas = primeros.length >= productosPorBloque;
    return primeros;
  }

  /// Mantiene la grilla al día con lo que pasa en el servidor.
  ///
  /// No se invalida el provider entero, que sería lo corto: eso vuelve a la
  /// primera página y le tira abajo a la persona todo lo que ya bajó, justo en
  /// el momento en que otro compró algo. Se cambia únicamente la ficha del
  /// producto que se movió, así el «Quedan 10» pasa a «Quedan 7» sin que se
  /// mueva nada más de la pantalla.
  void _escucharCambios() {
    final suscripcion = ref.watch(eventosRealtimeProvider).listen((evento) {
      if (agregadosDeCatalogo.contains(evento.aggregate)) {
        unawaited(_aplicar(evento));
      }
    });
    ref.onDispose(suscripcion.cancel);
  }

  Future<void> _aplicar(RealtimeEvent evento) async {
    final actuales = state.value;
    final id = evento.aggregateId;
    if (actuales == null || id == null) return;
    final indice = actuales.indexWhere((producto) => producto.id == id);
    if (evento.type.endsWith('_DELETED')) {
      if (indice >= 0) state = AsyncData([...actuales]..removeAt(indice));
      return;
    }
    // Uno que no está en pantalla solo importa si acaba de nacer; para el
    // resto, lo que cambió no se ve desde acá y pedirlo sería una consulta al
    // pedo.
    if (indice < 0) {
      if (evento.type.endsWith('_CREATED')) await _recargarLoMostrado();
      return;
    }
    try {
      final actualizado = await ref.read(catalogRepositoryProvider).get(id);
      final copia = [...actuales];
      copia[indice] = actualizado;
      state = AsyncData(copia);
    } on Object {
      // Que no se pueda traer la ficha nueva no es motivo para vaciar la
      // grilla: se queda la anterior hasta el próximo aviso o refresco.
    }
  }

  /// Vuelve a pedir los bloques que ya están en pantalla, sin perder ninguno.
  Future<void> _recargarLoMostrado() async {
    final repositorio = ref.read(catalogRepositoryProvider);
    final acumulado = <Product>[];
    for (var pagina = 0; pagina <= _pagina; pagina++) {
      try {
        final bloque = await repositorio.list(
          search: consulta.search,
          brandId: consulta.brandId,
          page: pagina,
        );
        acumulado.addAll(bloque);
        if (bloque.length < productosPorBloque) {
          _pagina = pagina;
          _hayMas = false;
          break;
        }
      } on Object {
        return;
      }
    }
    state = AsyncData(acumulado);
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
