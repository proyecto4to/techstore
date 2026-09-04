import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:techstore_mobile/core/di/providers.dart';
import 'package:techstore_mobile/core/logging/app_logger.dart';
import 'package:techstore_mobile/core/network/api_client.dart';
import 'package:techstore_mobile/core/storage/secure_store.dart';
import 'package:techstore_mobile/features/chat/presentation/chat_page.dart';

/// La sesión se arma con el `SessionController` de verdad, restaurando un
/// refresh guardado: así el caso ejercita el mismo camino que la app y no una
/// copia del controlador hecha para el test.
void main() {
  Widget conSesion(
    String rol, {
    List<Map<String, Object?>> conversaciones = const [],
  }) {
    final dio = Dio()..httpClientAdapter = _AdaptadorDeRol(rol);
    return ProviderScope(
      overrides: [
        apiClientProvider.overrideWithValue(
          ApiClient(
            baseUrl: 'https://techstore.local',
            logger: const _LoggerMudo(),
            dio: dio,
          ),
        ),
        secureStoreProvider.overrideWithValue(_StoreConSesion()),
        conversationsProvider.overrideWith((ref) async => conversaciones),
      ],
      child: const MaterialApp(home: ChatPage()),
    );
  }

  group('lo que ve quien compra', () {
    testWidgets('ve «Atención al Cliente» y nunca la palabra Administrador', (
      tester,
    ) async {
      await tester.pumpWidget(conSesion('Cliente'));
      await tester.pumpAndSettle();

      expect(find.text('Atención al Cliente'), findsWidgets);
      // La organización interna de la tienda no es asunto del cliente.
      expect(find.textContaining('Administrador'), findsNothing);
      expect(find.textContaining('Admin'), findsNothing);
    });

    testWidgets('no le ofrecen elegir con quién habla', (tester) async {
      await tester.pumpWidget(conSesion('Cliente'));
      await tester.pumpAndSettle();

      expect(find.byType(FloatingActionButton), findsNothing);
      expect(find.textContaining('Nueva'), findsNothing);
    });

    testWidgets('sin mensajes previos igual lo invitan a escribir', (
      tester,
    ) async {
      await tester.pumpWidget(conSesion('Cliente'));
      await tester.pumpAndSettle();

      expect(find.text('Escribinos y te respondemos.'), findsOneWidget);
    });
  });

  group('lo que ve la tienda', () {
    testWidgets('el cajero ve la bandeja de conversaciones', (tester) async {
      await tester.pumpWidget(conSesion('Cajero'));
      await tester.pumpAndSettle();

      expect(find.text('Conversaciones'), findsOneWidget);
      expect(find.text('Atención al Cliente'), findsNothing);
    });

    testWidgets('no hay botón de nueva conversación para la tienda', (
      tester,
    ) async {
      // El servidor solo deja que la conversación la inicie el cliente, así que
      // ese botón únicamente podía terminar en un error.
      await tester.pumpWidget(conSesion('Admin'));
      await tester.pumpAndSettle();

      expect(find.byType(FloatingActionButton), findsNothing);
    });

    testWidgets('el estado vacío dice algo útil', (tester) async {
      await tester.pumpWidget(conSesion('Admin'));
      await tester.pumpAndSettle();

      expect(find.text('Ningún cliente escribió todavía'), findsOneWidget);
    });

    testWidgets('cada conversación se identifica por el cliente', (
      tester,
    ) async {
      await tester.pumpWidget(
        conSesion(
          'Admin',
          conversaciones: [
            {
              'id': 3,
              'clienteNombre': 'Ana Ruiz',
              'noLeidos': 2,
              'pedidoNumero': 69,
              'ultimoMensaje': {'contenido': '¿Cuándo llega?'},
            },
          ],
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('Ana Ruiz'), findsOneWidget);
      expect(find.textContaining('Pedido N.º 69'), findsOneWidget);
      expect(find.text('2'), findsOneWidget);
    });
  });
}

final class _AdaptadorDeRol implements HttpClientAdapter {
  _AdaptadorDeRol(this.rol);

  final String rol;

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
    return ResponseBody.fromString(
      jsonEncode(const {'title': 'No encontrado', 'status': 404}),
      404,
      headers: {
        Headers.contentTypeHeader: ['application/json'],
      },
    );
  }

  @override
  void close({bool force = false}) {}
}

final class _StoreConSesion implements SecureStore {
  String? value = 'refresh-guardado';

  @override
  Future<void> clear() async => value = null;

  @override
  Future<void> delete(String key) async => value = null;

  @override
  Future<String?> read(String key) async => value;

  @override
  Future<void> write(String key, String value) async => this.value = value;
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
