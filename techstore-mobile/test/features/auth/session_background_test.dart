import 'dart:async';
import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:techstore_mobile/core/di/providers.dart';
import 'package:techstore_mobile/core/logging/app_logger.dart';
import 'package:techstore_mobile/core/native/app_excursion.dart';
import 'package:techstore_mobile/core/network/api_client.dart';
import 'package:techstore_mobile/core/storage/secure_store.dart';
import 'package:techstore_mobile/features/auth/application/auth_state.dart';
import 'package:techstore_mobile/features/auth/application/session_controller.dart';

void main() {
  group('cierre de sesión al pasar a segundo plano', () {
    test('borra el token guardado y deja la sesión sin autenticar', () async {
      final adapter = _AuthAdapter();
      final store = _MemorySecureStore();
      final container = _contenedor(adapter, store);

      await _entrar(container);
      expect(
        container.read(authSessionProvider.notifier).accessToken,
        isNotNull,
        reason: 'arranca con sesión',
      );

      await container.read(authSessionProvider.notifier).closeForBackground();

      final estado = container.read(authSessionProvider);
      expect(estado.status, AuthStatus.unauthenticated);
      expect(estado.user, isNull);
      // Lo que no puede quedar es la credencial en el teléfono.
      expect(store.value, isNull);
      expect(container.read(authSessionProvider.notifier).accessToken, isNull);
      expect(adapter.logoutRequests, 1, reason: 'avisa al servidor');
    });

    test('el mensaje explica por qué hay que volver a entrar', () async {
      final container = _contenedor(_AuthAdapter(), _MemorySecureStore());
      await _entrar(container);

      await container.read(authSessionProvider.notifier).closeForBackground();

      final mensaje = container.read(authSessionProvider).message;
      expect(mensaje, isNotNull);
      expect(mensaje, contains('Ingresá de nuevo'));
    });

    test('sin sesión abierta no hace nada ni llama al servidor', () async {
      final adapter = _AuthAdapter();
      final container = _contenedor(adapter, _MemorySecureStore());
      container.read(authSessionProvider);
      await _esperar(
        () =>
            container.read(authSessionProvider).status ==
            AuthStatus.unauthenticated,
      );

      await container.read(authSessionProvider.notifier).closeForBackground();

      expect(adapter.logoutRequests, 0);
    });

    test(
      'si el servidor no responde, la sesión local igual queda cerrada',
      () async {
        final adapter = _AuthAdapter(logoutFalla: true);
        final store = _MemorySecureStore();
        final container = _contenedor(adapter, store);
        await _entrar(container);

        await container.read(authSessionProvider.notifier).closeForBackground();

        expect(
          container.read(authSessionProvider).status,
          AuthStatus.unauthenticated,
        );
        expect(store.value, isNull);
      },
    );
  });

  group('mantener sesión iniciada', () {
    test('sin la casilla, el token no se guarda en el teléfono', () async {
      final store = _MemorySecureStore();
      final container = _contenedor(_AuthAdapter(), store);

      await _entrar(container);

      expect(store.value, isNull);
      expect(store.mantener, isFalse);
    });

    test('con la casilla, salir de la app no cierra la sesión', () async {
      final adapter = _AuthAdapter();
      final store = _MemorySecureStore();
      final container = _contenedor(adapter, store);

      await _entrar(container, mantener: true);
      await container.read(authSessionProvider.notifier).closeForBackground();

      expect(
        container.read(authSessionProvider).status,
        AuthStatus.authenticated,
      );
      expect(store.value, isNotNull, reason: 'el token queda guardado');
      expect(store.mantener, isTrue);
      expect(adapter.logoutRequests, 0);
    });

    test('al reabrir la app, la sesión mantenida se restaura sola', () async {
      final store = _MemorySecureStore()
        ..value = 'refresh-inicial'
        ..mantener = true;
      final container = _contenedor(_AuthAdapter(), store);

      container.read(authSessionProvider);
      await _esperar(
        () =>
            container.read(authSessionProvider).status ==
            AuthStatus.authenticated,
      );
      expect(container.read(authSessionProvider.notifier).keepSignedIn, isTrue);
    });

    test('un token guardado sin la casilla no abre la sesión solo', () async {
      // Es lo que dejaba la versión anterior, que guardaba el token siempre.
      final store = _MemorySecureStore()
        ..value = 'refresh-viejo'
        ..mantener = false;
      final adapter = _AuthAdapter();
      final container = _contenedor(adapter, store);

      container.read(authSessionProvider);
      await _esperar(
        () =>
            container.read(authSessionProvider).status ==
            AuthStatus.unauthenticated,
      );
      expect(store.value, isNull);
      expect(adapter.refreshRequests, 0);
    });

    test('cerrar sesión olvida la casilla', () async {
      final store = _MemorySecureStore();
      final container = _contenedor(_AuthAdapter(), store);
      await _entrar(container, mantener: true);

      await container.read(authSessionProvider.notifier).logout();

      expect(store.value, isNull);
      expect(store.mantener, isFalse);
    });
  });

  group('salidas que provoca la propia app', () {
    test('mientras dura una excursión queda marcada', () async {
      final excursion = AppExcursion();
      expect(excursion.enCurso, isFalse);

      final terminar = Completer<void>();
      final corriendo = excursion.durante(() => terminar.future);
      expect(excursion.enCurso, isTrue);

      terminar.complete();
      await corriendo;
      expect(excursion.enCurso, isFalse);
    });

    test(
      'dos excursiones encadenadas no se destapan antes de tiempo',
      () async {
        // Elegir la foto y después recortarla son dos salidas seguidas: si la
        // primera levantara la marca, el recorte cerraría la sesión.
        final excursion = AppExcursion();
        final primera = Completer<void>();
        final segunda = Completer<void>();
        final a = excursion.durante(() => primera.future);
        final b = excursion.durante(() => segunda.future);

        primera.complete();
        await a;
        expect(excursion.enCurso, isTrue, reason: 'la segunda sigue abierta');

        segunda.complete();
        await b;
        expect(excursion.enCurso, isFalse);
      },
    );

    test('si la operación falla, la marca igual se levanta', () async {
      final excursion = AppExcursion();
      await expectLater(
        excursion.durante(() async => throw StateError('cancelado')),
        throwsStateError,
      );
      expect(excursion.enCurso, isFalse);
    });
  });
}

ProviderContainer _contenedor(_AuthAdapter adapter, SecureStore store) {
  final dio = Dio()..httpClientAdapter = adapter;
  final client = ApiClient(
    baseUrl: 'https://techstore.local',
    logger: const _SilentLogger(),
    dio: dio,
  );
  final container = ProviderContainer(
    overrides: [
      apiClientProvider.overrideWithValue(client),
      secureStoreProvider.overrideWithValue(store),
    ],
  );
  addTearDown(container.dispose);
  return container;
}

Future<void> _esperar(bool Function() condicion) async {
  for (var intento = 0; intento < 40; intento++) {
    if (condicion()) return;
    await Future<void>.delayed(const Duration(milliseconds: 20));
  }
  fail('La condición no se cumplió a tiempo');
}

/// Entra con correo y contraseña, como en la pantalla de ingreso.
Future<void> _entrar(
  ProviderContainer container, {
  bool mantener = false,
}) async {
  container.read(authSessionProvider);
  await _esperar(() => !container.read(authSessionProvider).isBusy);
  final ok = await container
      .read(authSessionProvider.notifier)
      .login(
        email: 'cajero@techstore.py',
        password: 'secreta',
        keepSignedIn: mantener,
      );
  expect(ok, isTrue);
}

final class _AuthAdapter implements HttpClientAdapter {
  _AuthAdapter({this.logoutFalla = false});

  final bool logoutFalla;
  int refreshRequests = 0;
  int logoutRequests = 0;

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    if (options.path.contains('/auth/logout')) {
      logoutRequests++;
      if (logoutFalla) return _json(503, {'title': 'Sin servicio'});
      return _json(200, const {});
    }
    if (options.path.endsWith('/auth/login') ||
        options.path.endsWith('/auth/refresh')) {
      if (options.path.endsWith('/auth/refresh')) refreshRequests++;
      return _json(200, {
        'accessToken': 'access-$refreshRequests',
        'refreshToken': 'refresh-$refreshRequests',
        'tokenType': 'Bearer',
        'expiresInMs': 900000,
        'usuario': {
          'id': 1,
          'email': 'cajero@techstore.py',
          'nombre': 'Caja',
          'apellido': 'Mostrador',
          'rol': 'Cajero',
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

/// Guarda por separado el token y la marca de «mantener sesión», como el
/// almacén real.
final class _MemorySecureStore implements SecureStore {
  /// El token de renovación guardado.
  String? value;
  bool mantener = false;

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
