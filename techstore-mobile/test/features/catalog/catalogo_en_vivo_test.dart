import 'dart:async';
import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:techstore_mobile/app/realtime_coordinator.dart';
import 'package:techstore_mobile/core/network/api_client.dart';
import 'package:techstore_mobile/core/realtime/realtime_event.dart';
import 'package:techstore_mobile/features/catalog/application/catalog_providers.dart';
import 'package:techstore_mobile/features/catalog/data/catalog_repository.dart';

import '../../support/sesion_de_prueba.dart';

/// El catálogo se mantiene solo cuando alguien compra.
///
/// Es el caso que el usuario describió: hay diez unidades, otro compra tres, y
/// quien está mirando la pantalla tiene que ver siete sin tocar nada. Toda la
/// cañería de tiempo real ya existía; lo que fallaba era el último paso, que
/// comparaba los códigos del servidor —`PRODUCT`, en inglés— contra la palabra
/// «producto», no coincidía nunca, y no refrescaba nada.
void main() {
  const consulta = (search: '', brandId: null);

  ({ProviderContainer contenedor, StreamController<RealtimeEvent> eventos})
  armar({required int stockInicial, required int stockDespues}) {
    final eventos = StreamController<RealtimeEvent>.broadcast();
    final adaptador = _CatalogoFalso(
      stockInicial: stockInicial,
      stockDespues: stockDespues,
    );
    final contenedor = ProviderContainer(
      overrides: [
        // Se sustituye el repositorio y no el cliente HTTP porque el de verdad
        // arrastra el entorno y la base local, que acá no hacen falta: lo que
        // se está probando es qué hace la grilla cuando llega un evento.
        catalogRepositoryProvider.overrideWithValue(
          CatalogRepository(
            ApiClient(
              baseUrl: 'https://techstore.local',
              logger: const LoggerMudo(),
              dio: Dio()..httpClientAdapter = adaptador,
            ),
          ),
        ),
        eventosRealtimeProvider.overrideWithValue(eventos.stream),
      ],
    );
    // Alguien tiene que quedar escuchando: el provider se descarta solo en
    // cuanto nadie lo mira, y entonces no hay grilla que actualizar. En la app
    // ese alguien es la pantalla abierta.
    contenedor.listen(catalogProvider(consulta), (anterior, actual) {});
    addTearDown(contenedor.dispose);
    addTearDown(eventos.close);
    return (contenedor: contenedor, eventos: eventos);
  }

  test('un cambio de stock se ve sin volver a entrar', () async {
    final caso = armar(stockInicial: 10, stockDespues: 7);
    final antes = await caso.contenedor.read(catalogProvider(consulta).future);
    expect(antes.single.maxPerOrder, 10);

    caso.eventos.add(
      const RealtimeEvent(
        id: 1,
        type: 'STOCK_UPDATED',
        aggregate: 'PRODUCT',
        aggregateId: 5,
      ),
    );
    await pumpEventQueue();

    expect(
      caso.contenedor.read(catalogProvider(consulta)).value!.single.maxPerOrder,
      7,
      reason:
          'El servidor avisó del cambio y la grilla siguió mostrando el stock '
          'viejo: es exactamente lo que pasaba cuando los códigos no coincidían.',
    );
  });

  test('quedarse sin unidades también llega solo', () async {
    final caso = armar(stockInicial: 3, stockDespues: 0);
    await caso.contenedor.read(catalogProvider(consulta).future);

    caso.eventos.add(
      const RealtimeEvent(
        id: 2,
        type: 'PRODUCT_OUT_OF_STOCK',
        aggregate: 'PRODUCT',
        aggregateId: 5,
      ),
    );
    await pumpEventQueue();

    final producto = caso.contenedor
        .read(catalogProvider(consulta))
        .value!
        .single;
    expect(producto.maxPerOrder, 0);
    expect(producto.available, isFalse);
  });

  test('un evento de otra parte del sistema no toca el catálogo', () async {
    final caso = armar(stockInicial: 10, stockDespues: 7);
    await caso.contenedor.read(catalogProvider(consulta).future);

    caso.eventos.add(
      const RealtimeEvent(
        id: 3,
        type: 'CASH_MOVEMENT_CREATED',
        aggregate: 'CASH_MOVEMENT',
        aggregateId: 5,
      ),
    );
    await pumpEventQueue();

    expect(
      caso.contenedor.read(catalogProvider(consulta)).value!.single.maxPerOrder,
      10,
      reason: 'un movimiento de caja no cambia lo que hay en el estante',
    );
  });

  test('un producto borrado desaparece de la grilla', () async {
    final caso = armar(stockInicial: 10, stockDespues: 10);
    await caso.contenedor.read(catalogProvider(consulta).future);

    caso.eventos.add(
      const RealtimeEvent(
        id: 4,
        type: 'PRODUCT_DELETED',
        aggregate: 'PRODUCT',
        aggregateId: 5,
      ),
    );
    await pumpEventQueue();

    expect(caso.contenedor.read(catalogProvider(consulta)).value, isEmpty);
  });
}

/// Un catálogo de un solo producto que cambia de stock al pedirlo de nuevo.
///
/// La lista siempre contesta el stock inicial y la ficha suelta el de después:
/// así se distingue si la pantalla se actualizó de verdad o si simplemente
/// volvió a pedir la lista entera.
final class _CatalogoFalso implements HttpClientAdapter {
  _CatalogoFalso({required this.stockInicial, required this.stockDespues});

  final int stockInicial;
  final int stockDespues;

  Map<String, Object?> _producto(int stock) => {
    'id': 5,
    'nombre': 'Teclado mecánico',
    'descripcion': 'Para probar',
    'precio': 350000,
    'moneda': 'PYG',
    'disponible': stock > 0,
    'maxPorPedido': stock,
    'imagenUrl': null,
    'marca': null,
  };

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    final esFicha = RegExp(r'/productos/\d+$').hasMatch(options.path);
    final cuerpo = esFicha
        ? _producto(stockDespues)
        : {
            'content': [_producto(stockInicial)],
          };
    return ResponseBody.fromString(
      jsonEncode(cuerpo),
      200,
      headers: {
        Headers.contentTypeHeader: ['application/json'],
      },
    );
  }

  @override
  void close({bool force = false}) {}
}
