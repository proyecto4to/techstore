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

void main() {
  test(
    'credenciales incorrectas no se presentan como una sesión vencida',
    () async {
      final client = ApiClient(
        baseUrl: 'https://techstore.local',
        logger: const _SilentLogger(),
        dio: Dio()..httpClientAdapter = _AuthAdapter(),
      );
      final container = ProviderContainer(
        overrides: [
          apiClientProvider.overrideWithValue(client),
          secureStoreProvider.overrideWithValue(_MemorySecureStore()),
        ],
      );
      addTearDown(container.dispose);
      container.read(authSessionProvider);
      await _waitFor(() => !container.read(authSessionProvider).isBusy);
      final ok = await container
          .read(authSessionProvider.notifier)
          .login(email: 'persona@example.invalid', password: 'incorrecta');
      expect(ok, isFalse);
      expect(
        container.read(authSessionProvider).message,
        'El correo o la contraseña no son correctos. Revisalos e intentá de nuevo.',
      );
    },
  );
  test(
    'restaura y rota refresh una sola vez para solicitudes concurrentes',
    () async {
      final adapter = _AuthAdapter();
      final dio = Dio()..httpClientAdapter = adapter;
      final client = ApiClient(
        baseUrl: 'https://techstore.local',
        logger: const _SilentLogger(),
        dio: dio,
      );
      final store = _MemorySecureStore()..value = 'refresh-inicial';
      final container = ProviderContainer(
        overrides: [
          apiClientProvider.overrideWithValue(client),
          secureStoreProvider.overrideWithValue(store),
        ],
      );
      addTearDown(container.dispose);

      container.read(authSessionProvider);
      await _waitFor(
        () =>
            container.read(authSessionProvider).status ==
            AuthStatus.authenticated,
      );
      expect(adapter.refreshRequests, 1);
      expect(store.value, 'refresh-1');

      final controller = container.read(authSessionProvider.notifier);
      final results = await Future.wait([
        controller.refreshAccessToken(),
        controller.refreshAccessToken(),
        controller.refreshAccessToken(),
      ]);

      expect(adapter.refreshRequests, 2);
      expect(results.toSet(), {'access-2'});
      expect(store.value, 'refresh-2');
    },
  );
}

Future<void> _waitFor(bool Function() condition) async {
  for (var attempt = 0; attempt < 30; attempt++) {
    if (condition()) return;
    await Future<void>.delayed(const Duration(milliseconds: 20));
  }
  fail('La condición no se cumplió a tiempo');
}

final class _AuthAdapter implements HttpClientAdapter {
  int refreshRequests = 0;

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    if (options.path.endsWith('/auth/login')) {
      return _json(401, {'title': 'No autorizado', 'status': 401});
    }
    if (options.path.endsWith('/auth/refresh')) {
      refreshRequests++;
      await Future<void>.delayed(const Duration(milliseconds: 40));
      return _json(200, {
        'accessToken': 'access-$refreshRequests',
        'refreshToken': 'refresh-$refreshRequests',
        'tokenType': 'Bearer',
        'expiresInMs': 900000,
        'usuario': {
          'id': 1,
          'email': 'admin@techstore.py',
          'nombre': 'Admin',
          'apellido': 'Sistema',
          'rol': 'Admin',
          'ruc': null,
          'razonSocial': null,
        },
      });
    }
    return _json(404, {'title': 'No encontrado', 'status': 404});
  }

  ResponseBody _json(int status, Map<String, Object?> value) =>
      ResponseBody.fromString(
        jsonEncode(value),
        status,
        headers: {
          Headers.contentTypeHeader: ['application/json'],
        },
      );

  @override
  void close({bool force = false}) {}
}

final class _MemorySecureStore implements SecureStore {
  String? value;

  @override
  Future<void> clear() async => value = null;

  @override
  Future<void> delete(String key) async => value = null;

  @override
  Future<String?> read(String key) async => value;

  @override
  Future<void> write(String key, String value) async => this.value = value;
}

final class _SilentLogger implements AppLogger {
  const _SilentLogger();

  @override
  void debug(String message) {}

  @override
  void error(String message, [Object? error, StackTrace? stackTrace]) {}

  @override
  void info(String message) {}

  @override
  void warning(String message, [Object? error, StackTrace? stackTrace]) {}
}
