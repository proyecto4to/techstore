import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:techstore_mobile/core/di/providers.dart';
import 'package:techstore_mobile/core/network/api_client.dart';
import 'package:techstore_mobile/features/auth/application/session_controller.dart';
import 'package:techstore_mobile/features/courier/presentation/courier_dashboard_page.dart';

import '../../support/sesion_de_prueba.dart';

/// El panel del repartidor escondía el menú entero detrás de la consulta de
/// estadísticas: si esa llamada fallaba, no quedaba forma de entrar a las
/// entregas ni a la ruta, que es lo que se usa en la calle. Los números son
/// una tarjeta; el menú tiene que estar igual.
void main() {
  testWidgets('con las estadísticas caídas el menú sigue estando', (
    tester,
  ) async {
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          apiClientProvider.overrideWithValue(
            ApiClient(
              baseUrl: 'https://techstore.local',
              logger: const LoggerMudo(),
              dio: Dio()..httpClientAdapter = _PanelCaido(),
            ),
          ),
          secureStoreProvider.overrideWithValue(StoreConSesion()),
        ],
        child: MaterialApp(
          home: Consumer(
            builder: (context, ref, _) =>
                ref.watch(authSessionProvider).isAuthenticated
                ? const CourierDashboardPage()
                : const SizedBox.shrink(),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('No pudimos cargar los números de tu jornada'), findsOne);

    // La lista es más larga que la pantalla, así que cada entrada se busca
    // desplazando: lo que importa es que estén, no que entren todas juntas.
    for (final entrada in const [
      'Mis entregas',
      'Ruta optimizada',
      'Caja y transferencias',
      'Vehículos',
      'Mensajes',
      'Mi cuenta',
    ]) {
      await tester.scrollUntilVisible(
        find.text(entrada),
        120,
        scrollable: find.byType(Scrollable).first,
      );
      expect(find.text(entrada), findsOne, reason: 'falta «$entrada»');
    }
  });
}

/// Sesión válida, estadísticas rotas: exactamente el caso que dejaba al
/// repartidor sin navegación.
final class _PanelCaido implements HttpClientAdapter {
  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    if (options.path.endsWith('/auth/refresh')) {
      return _json(200, {
        'accessToken': 'access',
        'refreshToken': 'refresh',
        'tokenType': 'Bearer',
        'expiresInMs': 900000,
        'usuario': {
          'id': 9,
          'email': 'repartidor@techstore.py',
          'nombre': 'Repartidor',
          'apellido': 'De Prueba',
          'rol': 'Repartidor',
          'ruc': null,
          'razonSocial': null,
        },
      });
    }
    if (options.path.contains('/repartos/dashboard')) {
      return _json(500, {'title': 'Error', 'status': 500});
    }
    return _json(200, const <String, Object?>{});
  }

  ResponseBody _json(int status, Map<String, Object?> cuerpo) =>
      ResponseBody.fromString(
        jsonEncode(cuerpo),
        status,
        headers: {
          Headers.contentTypeHeader: ['application/json'],
        },
      );

  @override
  void close({bool force = false}) {}
}
