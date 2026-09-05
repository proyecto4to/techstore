import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:techstore_mobile/core/logging/app_logger.dart';
import 'package:techstore_mobile/core/network/api_client.dart';
import 'package:techstore_mobile/features/catalog/application/catalog_providers.dart';
import 'package:techstore_mobile/features/catalog/data/catalog_repository.dart';

/// El catálogo se trae de a bloques.
///
/// Antes pedía una sola página de treinta y no había forma de ver el resto: el
/// repositorio aceptaba `page` pero nadie se lo pasaba. Estos casos fijan que
/// se pida el bloque siguiente, que se acumule con lo que ya se mostró y que se
/// deje de pedir cuando el bloque viene incompleto.
///
/// Se falsea el HTTP y no el repositorio —que además es una clase final— para
/// que el caso recorra el repositorio de verdad, con su armado de la consulta.
void main() {
  const consulta = (search: '', brandId: null);

  ({ProviderContainer container, _ServidorDeBloques servidor}) conBloques(
    List<int> tamanos, {
    bool fallaDespues = false,
  }) {
    final servidor = _ServidorDeBloques(tamanos, fallaDespues: fallaDespues);
    final container = ProviderContainer(
      overrides: [
        catalogRepositoryProvider.overrideWith(
          (ref) => CatalogRepository(
            ApiClient(
              baseUrl: 'https://techstore.local',
              logger: const _LoggerMudo(),
              dio: Dio()..httpClientAdapter = servidor,
            ),
            null,
            'test',
          ),
        ),
      ],
    );
    addTearDown(container.dispose);
    // El provider es autoDispose: sin alguien escuchando se descarta entre una
    // lectura y la siguiente, y `read(...notifier)` devolvería un notifier
    // recién creado, con el contador de páginas en cero.
    container.listen(catalogProvider(consulta), (_, _) {}, fireImmediately: true);
    return (container: container, servidor: servidor);
  }

  test('el primer bloque completo deja la puerta abierta a pedir más', () async {
    final (:container, :servidor) = conBloques([30, 30]);

    final primeros = await container.read(catalogProvider(consulta).future);

    expect(primeros, hasLength(30));
    expect(container.read(catalogProvider(consulta).notifier).hayMas, isTrue);
    expect(servidor.paginasPedidas, [0]);
  });

  test('un bloque incompleto significa que no hay más', () async {
    final (:container, :servidor) = conBloques([12]);

    await container.read(catalogProvider(consulta).future);

    // Doce de treinta ya es el final: pedir otra página sería una consulta
    // segura de volver vacía.
    expect(container.read(catalogProvider(consulta).notifier).hayMas, isFalse);
    expect(servidor.paginasPedidas, [0]);
  });

  test('cargarMas suma el bloque siguiente sin perder lo anterior', () async {
    final (:container, :servidor) = conBloques([30, 30]);
    await container.read(catalogProvider(consulta).future);

    await container.read(catalogProvider(consulta).notifier).cargarMas();

    final acumulados = container.read(catalogProvider(consulta)).value!;
    expect(acumulados, hasLength(60));
    expect(acumulados.first.id, 1);
    expect(acumulados.last.id, 60);
    expect(servidor.paginasPedidas, [0, 1]);
  });

  test('sin más bloques, cargarMas no vuelve a consultar', () async {
    final (:container, :servidor) = conBloques([5]);
    await container.read(catalogProvider(consulta).future);

    await container.read(catalogProvider(consulta).notifier).cargarMas();

    expect(servidor.paginasPedidas, [0]);
  });

  test('si falla un bloque se conserva lo que ya estaba en pantalla', () async {
    final (:container, :servidor) = conBloques([30], fallaDespues: true);
    await container.read(catalogProvider(consulta).future);

    await container.read(catalogProvider(consulta).notifier).cargarMas();

    // Lo peor sería dejar la grilla vacía por no haber podido traer el bloque
    // siguiente: lo que ya se vio sigue ahí.
    expect(container.read(catalogProvider(consulta)).value, hasLength(30));
    expect(container.read(catalogProvider(consulta).notifier).hayMas, isFalse);
    expect(servidor.paginasPedidas, [0, 1]);
  });
}

/// Un servidor que devuelve bloques del tamaño pedido y anota qué páginas le
/// consultaron.
final class _ServidorDeBloques implements HttpClientAdapter {
  _ServidorDeBloques(this.tamanos, {this.fallaDespues = false});

  final List<int> tamanos;
  final bool fallaDespues;
  final List<int> paginasPedidas = [];

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    final pagina =
        int.tryParse(options.queryParameters['page']?.toString() ?? '0') ?? 0;
    paginasPedidas.add(pagina);

    if (pagina >= tamanos.length) {
      if (fallaDespues) {
        return ResponseBody.fromString(
          jsonEncode(const {'title': 'Se cayó', 'status': 503}),
          503,
          headers: {
            Headers.contentTypeHeader: ['application/json'],
          },
        );
      }
      return _pagina(const []);
    }

    var primero = 1;
    for (var i = 0; i < pagina; i++) {
      primero += tamanos[i];
    }
    return _pagina([
      for (var i = 0; i < tamanos[pagina]; i++)
        {
          'id': primero + i,
          'nombre': 'Producto ${primero + i}',
          'precio': 100000,
          'moneda': 'PYG',
          'disponible': true,
        },
    ]);
  }

  ResponseBody _pagina(List<Map<String, Object?>> contenido) =>
      ResponseBody.fromString(
        jsonEncode({'content': contenido}),
        200,
        headers: {
          Headers.contentTypeHeader: ['application/json'],
        },
      );

  @override
  void close({bool force = false}) {}
}

final class _LoggerMudo implements AppLogger {
  const _LoggerMudo();

  @override
  void debug(String message) {}

  @override
  void error(String message, [Object? error, StackTrace? stackTrace]) {}

  @override
  void info(String message) {}

  @override
  void warning(String message, [Object? error, StackTrace? stackTrace]) {}
}
