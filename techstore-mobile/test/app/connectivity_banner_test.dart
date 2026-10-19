import 'dart:convert';
import 'dart:typed_data';

import 'package:connectivity_plus/connectivity_plus.dart';
import 'package:dio/dio.dart';
import 'package:drift/native.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:techstore_mobile/app/connectivity_banner.dart';
import 'package:techstore_mobile/app/environment.dart';
import 'package:techstore_mobile/core/database/app_database.dart';
import 'package:techstore_mobile/core/di/providers.dart';
import 'package:techstore_mobile/core/network/connectivity_service.dart';
import 'package:techstore_mobile/core/storage/secure_store.dart';

final class _Conectividad extends Mock implements Connectivity {}

/// Responde 200 a todo: el backend está.
final class _BackendArriba implements HttpClientAdapter {
  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async => ResponseBody.fromString(
    jsonEncode({'content': <Object>[]}),
    200,
    headers: {
      Headers.contentTypeHeader: [Headers.jsonContentType],
    },
  );

  @override
  void close({bool force = false}) {}
}

final class _SinSesion implements SecureStore {
  @override
  Future<void> clear() async {}
  @override
  Future<void> delete(String key) async {}
  @override
  Future<String?> read(String key) async => null;
  @override
  Future<void> write(String key, String value) async {}
}

/// Monta el aviso como en la app —envolviendo cada pantalla— en un teléfono
/// con 24 puntos de barra de estado, y devuelve cuánto margen de arriba le
/// queda a la pantalla.
Future<double Function()> montar(WidgetTester tester, {required bool red}) async {
  tester.view
    ..devicePixelRatio = 3
    ..physicalSize = const Size(1236, 2745)
    ..padding = const FakeViewPadding(top: 72);
  addTearDown(tester.view.reset);

  final conectividad = _Conectividad();
  final resultado = [red ? ConnectivityResult.wifi : ConnectivityResult.none];
  when(conectividad.checkConnectivity).thenAnswer((_) async => resultado);
  when(
    () => conectividad.onConnectivityChanged,
  ).thenAnswer((_) => const Stream.empty());
  final base = AppDatabase(NativeDatabase.memory());
  addTearDown(base.close);

  var margen = -1.0;
  await tester.pumpWidget(
    ProviderScope(
      overrides: [
        environmentProvider.overrideWithValue(AppEnvironment.development()),
        databaseProvider.overrideWithValue(base),
        secureStoreProvider.overrideWithValue(_SinSesion()),
        connectivityProvider.overrideWithValue(
          ConnectivityService(
            conectividad,
            Dio()..httpClientAdapter = _BackendArriba(),
          ),
        ),
      ],
      child: MaterialApp(
        builder: (context, pantalla) => ConnectivityBanner(child: pantalla!),
        home: Builder(
          builder: (context) {
            margen = MediaQuery.paddingOf(context).top;
            return const Scaffold(body: SizedBox.expand());
          },
        ),
      ),
    ),
  );
  await tester.pumpAndSettle();
  return () => margen;
}

void main() {
  testWidgets('con el aviso a la vista, la pantalla no vuelve a dejar el '
      'lugar de la barra de estado', (tester) async {
    final margen = await montar(tester, red: false);
    expect(find.text('Sin conexión con el servidor'), findsOneWidget);
    // El aviso ya se corrió debajo de la barra: si la pantalla también lo
    // hiciera, quedaría una franja negra entre los dos.
    expect(margen(), 0);
  });

  testWidgets('sin aviso, la pantalla conserva su margen', (tester) async {
    final margen = await montar(tester, red: true);
    expect(find.textContaining('Sin conexión'), findsNothing);
    expect(margen(), 24);
  });
}
