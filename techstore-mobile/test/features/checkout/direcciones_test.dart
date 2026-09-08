import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:techstore_mobile/core/di/providers.dart';
import 'package:techstore_mobile/core/network/api_client.dart';
import 'package:techstore_mobile/features/checkout/application/checkout_providers.dart';
import 'package:techstore_mobile/features/checkout/domain/checkout_models.dart';
import 'package:techstore_mobile/features/checkout/presentation/address_page.dart';

import '../../support/sesion_de_prueba.dart';

/// La lista de direcciones de quien compra.
///
/// Dos cosas que antes no estaban: la ficha se encabeza con el nombre que le
/// puso la persona —«Casa»— en vez de la calle, y borrar pregunta. Antes el
/// botón de borrar estaba pegado al de editar y borraba de una, sin aviso.
void main() {
  DeliveryAddress direccion({
    int id = 1,
    String? descripcion,
    String? calle = 'Avenida Mariscal López',
    bool principal = false,
  }) => DeliveryAddress(
    id: id,
    recipient: 'Ana Giménez',
    phone: '0981 123 456',
    description: descripcion,
    department: 'Central',
    city: 'Luque',
    line1: calle,
    latitude: -25.2637,
    longitude: -57.5759,
    primary: principal,
  );

  ({Widget vista, List<String> pedidos}) pantalla(
    List<DeliveryAddress> direcciones,
  ) {
    final pedidos = <String>[];
    final vista = ProviderScope(
      overrides: [
        secureStoreProvider.overrideWithValue(StoreConSesion()),
        apiClientProvider.overrideWithValue(
          ApiClient(
            baseUrl: 'https://techstore.local',
            logger: const LoggerMudo(),
            dio: Dio()..httpClientAdapter = _AdaptadorQueAnota(pedidos),
          ),
        ),
        addressesProvider.overrideWith((ref) async => direcciones),
      ],
      child: const MaterialApp(home: AddressPage()),
    );
    return (vista: vista, pedidos: pedidos);
  }

  testWidgets('la ficha se encabeza con el nombre del lugar', (tester) async {
    await tester.pumpWidget(pantalla([direccion(descripcion: 'Casa')]).vista);
    await tester.pumpAndSettle();

    expect(find.text('Casa'), findsOneWidget);
    expect(
      find.textContaining('Avenida Mariscal López'),
      findsOneWidget,
      reason: 'la calle sigue estando, debajo del nombre',
    );
  });

  testWidgets('sin nombre propio se muestra la calle', (tester) async {
    await tester.pumpWidget(pantalla([direccion()]).vista);
    await tester.pumpAndSettle();

    expect(find.text('Avenida Mariscal López'), findsWidgets);
  });

  testWidgets('la dirección principal se distingue', (tester) async {
    await tester.pumpWidget(
      pantalla([direccion(descripcion: 'Casa', principal: true)]).vista,
    );
    await tester.pumpAndSettle();

    expect(find.text('Principal'), findsOneWidget);
  });

  testWidgets('sin direcciones se invita a marcar la primera', (tester) async {
    await tester.pumpWidget(pantalla(const []).vista);
    await tester.pumpAndSettle();

    expect(find.textContaining('Todavía no guardaste'), findsOneWidget);
    expect(find.text('Marcar mi dirección'), findsOneWidget);
  });

  group('borrar', () {
    Future<void> abrirElMenuYBorrar(WidgetTester tester) async {
      await tester.tap(find.byIcon(Icons.more_vert));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Borrar'));
      await tester.pumpAndSettle();
    }

    testWidgets('pregunta antes, y cancelar no borra nada', (tester) async {
      final caso = pantalla([direccion(descripcion: 'Casa')]);
      await tester.pumpWidget(caso.vista);
      await tester.pumpAndSettle();

      await abrirElMenuYBorrar(tester);
      expect(find.text('Borrar la dirección'), findsOneWidget);
      expect(find.textContaining('«Casa»'), findsOneWidget);

      await tester.tap(find.text('Cancelar'));
      await tester.pumpAndSettle();

      expect(
        caso.pedidos,
        isEmpty,
        reason: 'cancelar no puede haber tocado el servidor',
      );
    });

    testWidgets('recién al confirmar se borra', (tester) async {
      final caso = pantalla([direccion(id: 7, descripcion: 'Casa')]);
      await tester.pumpWidget(caso.vista);
      await tester.pumpAndSettle();

      await abrirElMenuYBorrar(tester);
      await tester.tap(find.widgetWithText(FilledButton, 'Borrar'));
      await tester.pumpAndSettle();

      expect(caso.pedidos, ['DELETE /api/v1/direcciones/7']);
    });
  });
}

/// Anota qué le pidieron al servidor, para poder comprobar que no se pidió.
final class _AdaptadorQueAnota implements HttpClientAdapter {
  _AdaptadorQueAnota(this.pedidos);

  final List<String> pedidos;

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    pedidos.add('${options.method} ${options.path}');
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
