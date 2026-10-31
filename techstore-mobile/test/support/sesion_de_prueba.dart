import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:techstore_mobile/core/logging/app_logger.dart';
import 'package:techstore_mobile/core/network/api_client.dart';
import 'package:techstore_mobile/core/storage/secure_store.dart';

/// Una sesión iniciada de verdad, con el rol que pida cada caso.
///
/// `SessionController` es final y no se puede extender, así que en lugar de
/// falsificarlo se le da lo que necesita para restaurar sesión: un refresh
/// guardado y un servidor que responde a `/auth/refresh`. El caso termina
/// ejercitando el mismo camino que la app, no una copia hecha para el test.
ApiClient apiClientDeRol(
  String rol, {
  Map<String, Object?> respuestas = const {},
}) => ApiClient(
  baseUrl: 'https://techstore.local',
  logger: const LoggerMudo(),
  dio: Dio()..httpClientAdapter = AdaptadorDeRol(rol, respuestas: respuestas),
);

final class AdaptadorDeRol implements HttpClientAdapter {
  AdaptadorDeRol(this.rol, {this.respuestas = const {}});

  final String rol;

  /// Qué contestar a cada ruta, buscada por coincidencia parcial.
  ///
  /// Lo que no esté acá vuelve como un objeto vacío con 200, no como 404: hay
  /// pantallas que esconden todo su contenido si falla la primera consulta, y
  /// entonces el caso terminaría probando el estado de error en lugar de lo que
  /// quería mirar.
  final Map<String, Object?> respuestas;

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    if (options.path.endsWith('/auth/refresh')) {
      return ResponseBody.fromString(
        jsonEncode({
          'accessToken': 'access',
          'refreshToken': 'refresh',
          'tokenType': 'Bearer',
          'expiresInMs': 900000,
          'usuario': {
            'id': 1,
            'email': 'persona@techstore.py',
            'nombre': 'Persona',
            'apellido': 'De Prueba',
            'rol': rol,
            'ruc': null,
            'razonSocial': null,
          },
        }),
        200,
        headers: {
          Headers.contentTypeHeader: ['application/json'],
        },
      );
    }
    for (final entrada in respuestas.entries) {
      if (options.path.contains(entrada.key)) {
        return ResponseBody.fromString(
          jsonEncode(entrada.value),
          200,
          headers: {
            Headers.contentTypeHeader: ['application/json'],
          },
        );
      }
    }
    return ResponseBody.fromString(
      jsonEncode(const <String, Object?>{}),
      200,
      headers: {
        Headers.contentTypeHeader: ['application/json'],
      },
    );
  }

  @override
  void close({bool force = false}) {}
}

/// Un teléfono donde la persona entró con «Mantener sesión iniciada»: la app
/// arranca con la sesión restaurada, que es lo que necesitan las pruebas de
/// pantallas con sesión.
final class StoreConSesion implements SecureStore {
  /// El token de renovación guardado.
  String? value = 'refresh-guardado';
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

final class LoggerMudo implements AppLogger {
  const LoggerMudo();

  @override
  void debug(String message) {}

  @override
  void error(String message, [Object? error, StackTrace? stackTrace]) {}

  @override
  void info(String message) {}

  @override
  void warning(String message, [Object? error, StackTrace? stackTrace]) {}
}
