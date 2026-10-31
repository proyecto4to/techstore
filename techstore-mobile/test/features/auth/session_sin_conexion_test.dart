import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:techstore_mobile/core/di/providers.dart';
import 'package:techstore_mobile/core/logging/app_logger.dart';
import 'package:techstore_mobile/core/network/api_client.dart';
import 'package:techstore_mobile/core/storage/secure_store.dart';
import 'package:techstore_mobile/features/auth/application/auth_state.dart';
import 'package:techstore_mobile/features/auth/application/session_controller.dart';

/// Abrir la app con el servidor apagado no es lo mismo que tener la sesión
/// vencida. Antes las dos cosas terminaban igual: token borrado y el cartel
/// «Tu sesión expiró», así que un rato sin internet costaba volver a escribir
/// usuario y contraseña.
void main() {
  test('el servidor caído no borra la sesión guardada', () async {
    final contenedor = _contenedor(_AdaptadorCaido());
    addTearDown(contenedor.dispose);

    contenedor.read(authSessionProvider);
    await _esperarA(
      () =>
          contenedor.read(authSessionProvider).status ==
          AuthStatus.unauthenticated,
    );

    final estado = contenedor.read(authSessionProvider);
    expect(estado.message, contains('conectarnos'));
    expect(estado.message, isNot(contains('expiró')));
    expect(
      _almacen.value,
      'refresh-guardado',
      reason: 'el token tiene que sobrevivir a un problema de red',
    );
  });

  test('el rechazo del servidor sí cierra la sesión', () async {
    final contenedor = _contenedor(_AdaptadorRechaza());
    addTearDown(contenedor.dispose);

    contenedor.read(authSessionProvider);
    await _esperarA(
      () =>
          contenedor.read(authSessionProvider).status ==
          AuthStatus.unauthenticated,
    );

    expect(contenedor.read(authSessionProvider).message, contains('expiró'));
    expect(_almacen.value, isNull);
  });
}

late _AlmacenEnMemoria _almacen;

ProviderContainer _contenedor(HttpClientAdapter adaptador) {
  final dio = Dio()..httpClientAdapter = adaptador;
  final cliente = ApiClient(
    baseUrl: 'https://techstore.local',
    logger: const _RegistroMudo(),
    dio: dio,
  );
  _almacen = _AlmacenEnMemoria()..value = 'refresh-guardado';
  return ProviderContainer(
    overrides: [
      apiClientProvider.overrideWithValue(cliente),
      secureStoreProvider.overrideWithValue(_almacen),
    ],
  );
}

Future<void> _esperarA(bool Function() condicion) async {
  for (var intento = 0; intento < 30; intento++) {
    if (condicion()) return;
    await Future<void>.delayed(const Duration(milliseconds: 20));
  }
  fail('La condición no se cumplió a tiempo');
}

final class _AdaptadorCaido implements HttpClientAdapter {
  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    throw DioException.connectionError(
      requestOptions: options,
      reason: 'servidor apagado',
    );
  }

  @override
  void close({bool force = false}) {}
}

final class _AdaptadorRechaza implements HttpClientAdapter {
  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async => ResponseBody.fromString(
    jsonEncode({'title': 'No autorizado', 'status': 401}),
    401,
    headers: {
      Headers.contentTypeHeader: ['application/json'],
    },
  );

  @override
  void close({bool force = false}) {}
}

final class _AlmacenEnMemoria implements SecureStore {
  /// El token de renovación guardado. La marca de «mantener sesión» va
  /// encendida: es la única forma en que la app restaura una sesión.
  String? value;
  bool mantener = true;

  static const _claveMantener = 'techstore.keep_session';

  @override
  Future<void> clear() async {
    value = null;
    mantener = false;
  }

  @override
  Future<void> delete(String key) async =>
      key == _claveMantener ? mantener = false : value = null;

  @override
  Future<String?> read(String key) async =>
      key == _claveMantener ? (mantener ? 'true' : null) : value;

  @override
  Future<void> write(String key, String value) async =>
      key == _claveMantener ? mantener = value == 'true' : this.value = value;
}

final class _RegistroMudo implements AppLogger {
  const _RegistroMudo();

  @override
  void debug(String message) {}

  @override
  void error(String message, [Object? error, StackTrace? stackTrace]) {}

  @override
  void info(String message) {}

  @override
  void warning(String message, [Object? error, StackTrace? stackTrace]) {}
}
