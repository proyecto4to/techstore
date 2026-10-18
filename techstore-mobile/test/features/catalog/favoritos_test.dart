import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:techstore_mobile/core/di/providers.dart';
import 'package:techstore_mobile/core/network/api_client.dart';
import 'package:techstore_mobile/features/auth/application/session_controller.dart';
import 'package:techstore_mobile/features/catalog/domain/product.dart';
import 'package:techstore_mobile/features/catalog/presentation/vitrina/piezas_de_producto.dart';

import '../../support/sesion_de_prueba.dart';

const _producto = Product(
  id: 7,
  name: 'ASUS ROG Strix B650-A',
  price: 1850000,
  currency: 'PYG',
  available: true,
  maxPerOrder: 1,
);

/// La red de la prueba: restaura la sesión como `AdaptadorDeRol` y guarda los
/// favoritos en memoria, o corta la conexión a pedido.
final class _Red implements HttpClientAdapter {
  _Red({this.cortada = false});

  final bool cortada;
  final guardados = <int>{};
  final _sesion = AdaptadorDeRol('Cliente');

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    if (!options.path.contains('/favoritos')) {
      return _sesion.fetch(options, requestStream, cancelFuture);
    }
    if (options.method == 'GET') {
      return ResponseBody.fromString(
        jsonEncode([
          if (guardados.contains(_producto.id))
            {'producto': _producto.toJson()},
        ]),
        200,
        headers: {
          Headers.contentTypeHeader: ['application/json'],
        },
      );
    }
    if (cortada) {
      throw DioException(
        requestOptions: options,
        type: DioExceptionType.connectionError,
      );
    }
    final id = int.parse(options.path.split('/').last);
    if (options.method == 'POST') guardados.add(id);
    if (options.method == 'DELETE') guardados.remove(id);
    return ResponseBody.fromString('', 204);
  }

  @override
  void close({bool force = false}) {}
}

Widget _corazon(_Red red, {bool conSesion = true}) => ProviderScope(
  overrides: [
    apiClientProvider.overrideWithValue(
      ApiClient(
        baseUrl: 'https://techstore.local',
        logger: const LoggerMudo(),
        dio: Dio()..httpClientAdapter = red,
      ),
    ),
    secureStoreProvider.overrideWithValue(
      conSesion ? StoreConSesion() : (StoreConSesion()..value = null),
    ),
  ],
  child: MaterialApp(
    home: Scaffold(
      body: Consumer(
        builder: (context, ref, _) {
          // Se espera a que la sesión termine de restaurarse, como hace el
          // router en la app.
          if (ref.watch(authSessionProvider).isBusy) {
            return const SizedBox.shrink();
          }
          return const Center(child: BotonFavorito(producto: _producto));
        },
      ),
    ),
  ),
);

void main() {
  testWidgets('si el servidor confirma, el corazón queda marcado', (
    tester,
  ) async {
    final red = _Red();
    await tester.pumpWidget(_corazon(red));
    await tester.pumpAndSettle();

    await tester.tap(find.byTooltip('Guardar en favoritos'));
    await tester.pumpAndSettle();

    expect(red.guardados, {7});
    expect(find.byTooltip('Quitar de favoritos'), findsOneWidget);
  });

  testWidgets('si la red falla, vuelve a como estaba y lo dice', (
    tester,
  ) async {
    await tester.pumpWidget(_corazon(_Red(cortada: true)));
    await tester.pumpAndSettle();

    await tester.tap(find.byTooltip('Guardar en favoritos'));
    await tester.pump();
    // Cambia en el acto, sin esperar a la red…
    expect(find.byTooltip('Quitar de favoritos'), findsOneWidget);
    await tester.pumpAndSettle();

    // …y cuando el servidor no confirma, vuelve atrás y avisa. Antes el error
    // se perdía y nadie se enteraba de que no se había guardado.
    expect(find.byTooltip('Guardar en favoritos'), findsOneWidget);
    expect(find.text('No se pudo conectar con TechStore'), findsOneWidget);
  });

  testWidgets('sin sesión no intenta guardar: ofrece ingresar', (
    tester,
  ) async {
    final red = _Red();
    await tester.pumpWidget(_corazon(red, conSesion: false));
    await tester.pumpAndSettle();

    await tester.tap(find.byTooltip('Guardar en favoritos'));
    await tester.pump();

    expect(red.guardados, isEmpty);
    expect(find.text('Ingresá para guardar favoritos.'), findsOneWidget);
    expect(find.widgetWithText(SnackBarAction, 'Ingresar'), findsOneWidget);
  });
}
