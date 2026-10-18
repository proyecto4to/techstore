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

/// Cómo se ordena el catálogo. El servidor ya sabía ordenar; faltaba
/// ofrecerlo. Por precio ordena con el de lista, no con el que tiene la
/// promoción aplicada: es lo que el servidor puede ordenar en la base.
enum OrdenDeCatalogo {
  alfabetico(null, 'A–Z'),
  novedades('id,desc', 'Más nuevos'),
  precioMenor('precio,asc', 'Menor precio'),
  precioMayor('precio,desc', 'Mayor precio');

  const OrdenDeCatalogo(this.parametro, this.etiqueta);

  /// Lo que viaja en `sort`; nulo deja el orden por nombre del servidor.
  final String? parametro;
  final String etiqueta;
}

typedef ConsultaOrdenada = ({
  String search,
  int? brandId,
  OrdenDeCatalogo orden,
});

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

/// El mismo catálogo con un orden elegido: lo usa la vidriera del cliente. La
/// caja y las compras siguen con [catalogProvider], ordenado por nombre.
final catalogoOrdenadoProvider = AsyncNotifierProvider.autoDispose
    .family<CatalogoPaginado, List<Product>, ConsultaOrdenada>(
      (consulta) => CatalogoPaginado(
        (search: consulta.search, brandId: consulta.brandId),
        consulta.orden,
      ),
    );

class CatalogoPaginado extends AsyncNotifier<List<Product>> {
  CatalogoPaginado(this.consulta, [this.orden = OrdenDeCatalogo.alfabetico]);

  /// Qué se está mirando. En Riverpod 3 el argumento de la familia llega por el
  /// constructor, no por el `build`.
  final ConsultaDeCatalogo consulta;
  final OrdenDeCatalogo orden;

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
        .list(
          search: consulta.search,
          brandId: consulta.brandId,
          sort: orden.parametro,
        );
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
          sort: orden.parametro,
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
            sort: orden.parametro,
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

/// Una marca con lo que se sabe de ella en la vidriera.
typedef ColeccionDeVidriera = ({
  int? id,
  String nombre,
  int productos,
  Product muestra,
});

/// Lo que muestra el inicio del cliente, sacado entero de datos reales: no hay
/// «destacados» elegidos a mano ni categorías inventadas.
final class Vidriera {
  const Vidriera({
    required this.productos,
    required this.total,
    required this.marcas,
  });

  /// Los más nuevos primero.
  final List<Product> productos;

  /// Cuántos productos publica el catálogo en total.
  final int total;

  final List<Map<String, Object?>> marcas;

  /// Si [productos] es el catálogo entero. Solo entonces se pueden contar
  /// productos por marca sin mentir.
  bool get completo => productos.length >= total;

  List<Product> get ofertas =>
      productos.where((p) => p.hasPromotion).toList(growable: false);

  List<Product> get novedades => productos.take(10).toList(growable: false);

  /// Escasez real: quedan pocas unidades de verdad (ver [Product.quedaPoco]).
  List<Product> get ultimasUnidades => productos
      .where((p) => p.quedaPoco && p.available)
      .toList(growable: false);

  /// El carrusel principal: las ofertas, si hay; si no, lo recién llegado.
  List<Product> get destacados {
    final enOferta = ofertas;
    return (enOferta.isNotEmpty ? enOferta : productos)
        .where((p) => p.available)
        .take(4)
        .toList(growable: false);
  }

  /// Si el carrusel muestra ofertas o novedades: cambia el rótulo.
  bool get destacaOfertas => ofertas.isNotEmpty;

  /// Las marcas que tienen algo publicado, de la que más tiene a la que menos.
  List<ColeccionDeVidriera> get colecciones {
    final porMarca = <String, List<Product>>{};
    for (final p in productos) {
      final marca = p.brand;
      if (marca == null || marca.trim().isEmpty) continue;
      (porMarca[marca] ??= []).add(p);
    }
    int? idDe(String nombre) {
      for (final m in marcas) {
        if (m['nombre']?.toString() == nombre) {
          return (m['id'] as num?)?.toInt();
        }
      }
      return null;
    }

    final lista = [
      for (final MapEntry(key: nombre, value: suyos) in porMarca.entries)
        (
          id: idDe(nombre),
          nombre: nombre,
          productos: suyos.length,
          muestra: suyos.first,
        ),
    ]..sort((a, b) {
        final porCantidad = b.productos.compareTo(a.productos);
        return porCantidad != 0 ? porCantidad : a.nombre.compareTo(b.nombre);
      });
    return lista;
  }
}

/// Cuántos productos trae la vidriera. Con el catálogo de hoy (36) los ve a
/// todos; si un día pasa de esto, la vidriera muestra lo más nuevo y deja de
/// contar por marca, en lugar de contar mal.
const productosDeVidriera = 60;

final vidrieraProvider = FutureProvider.autoDispose<Vidriera>((ref) async {
  // Un precio o un stock que cambia en el servidor tiene que verse acá
  // también. Se recarga la vidriera entera: es una sola consulta.
  final suscripcion = ref.watch(eventosRealtimeProvider).listen((evento) {
    if (agregadosDeCatalogo.contains(evento.aggregate)) ref.invalidateSelf();
  });
  ref.onDispose(suscripcion.cancel);

  final repositorio = ref.watch(catalogRepositoryProvider);
  final (pagina, marcas) = await (
    repositorio.pagina(
      sort: OrdenDeCatalogo.novedades.parametro,
      size: productosDeVidriera,
    ),
    repositorio.brands().catchError((Object _) => <Map<String, Object?>>[]),
  ).wait;
  return Vidriera(
    productos: pagina.productos,
    total: pagina.total,
    marcas: marcas,
  );
});

typedef ConsultaDeRelacionados = ({int id, int? marcaId, String? marca});

/// Qué mostrar debajo de una ficha: más de la misma marca, que es la única
/// relación real que tienen los datos; si la marca no tiene otro producto, lo
/// recién llegado. El rótulo dice cuál de las dos cosas es.
final relacionadosProvider = FutureProvider.autoDispose
    .family<({String rotulo, List<Product> productos}), ConsultaDeRelacionados>(
      (ref, consulta) async {
        final repositorio = ref.watch(catalogRepositoryProvider);
        if (consulta.marcaId != null) {
          final deLaMarca = (await repositorio.list(brandId: consulta.marcaId))
              .where((p) => p.id != consulta.id)
              .take(10)
              .toList(growable: false);
          if (deLaMarca.isNotEmpty) {
            return (
              rotulo: 'más de ${consulta.marca ?? 'esta marca'}',
              productos: deLaMarca,
            );
          }
        }
        final nuevos =
            (await repositorio.list(sort: OrdenDeCatalogo.novedades.parametro))
                .where((p) => p.id != consulta.id)
                .take(10)
                .toList(growable: false);
        return (rotulo: 'recién llegados', productos: nuevos);
      },
    );
