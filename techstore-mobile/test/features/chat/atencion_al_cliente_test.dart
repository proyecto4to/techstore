import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:techstore_mobile/core/di/providers.dart';
import 'package:techstore_mobile/features/chat/presentation/chat_page.dart';

import '../../support/sesion_de_prueba.dart';

/// La sesión se arma con el `SessionController` de verdad, restaurando un
/// refresh guardado: así el caso ejercita el mismo camino que la app y no una
/// copia del controlador hecha para el test.
void main() {
  Widget conSesion(
    String rol, {
    List<Map<String, Object?>> conversaciones = const [],
  }) {
    return ProviderScope(
      overrides: [
        apiClientProvider.overrideWithValue(apiClientDeRol(rol)),
        secureStoreProvider.overrideWithValue(StoreConSesion()),
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

      expect(find.text('Contanos en qué te podemos ayudar'), findsOneWidget);
    });

    testWidgets(
      'abre la conversación directamente, sin una lista en el medio',
      (tester) async {
        await tester.pumpWidget(conSesion('Cliente'));
        await tester.pumpAndSettle();

        // Se puede escribir apenas se entra: el campo del mensaje ya está.
        expect(find.widgetWithText(TextField, 'Mensaje'), findsOneWidget);
        expect(find.byIcon(Icons.chevron_right), findsNothing);
      },
    );
  });

  group('lo que ve la tienda', () {
    testWidgets('el cajero ve la bandeja de conversaciones', (tester) async {
      await tester.pumpWidget(conSesion('Cajero'));
      await tester.pumpAndSettle();

      expect(find.text('Conversaciones'), findsOneWidget);
      expect(find.text('Atención al Cliente'), findsNothing);
    });

    testWidgets('la tienda puede empezar una conversación', (tester) async {
      // Administración habla con cualquiera y el resto del staff con
      // administración, así que el botón lleva a elegir con quién.
      await tester.pumpWidget(conSesion('Admin'));
      await tester.pumpAndSettle();

      expect(find.text('Nueva conversación'), findsOneWidget);
    });

    testWidgets('el estado vacío dice algo útil', (tester) async {
      await tester.pumpWidget(conSesion('Admin'));
      await tester.pumpAndSettle();

      expect(find.text('Todavía no hay conversaciones'), findsOneWidget);
    });

    testWidgets('cada conversación se identifica por su título', (
      tester,
    ) async {
      // El título lo resuelve el servidor: en una consulta de cliente es el
      // nombre de quien escribió, y en una privada el de la otra persona.
      await tester.pumpWidget(
        conSesion(
          'Admin',
          conversaciones: [
            {
              'id': 3,
              'tipo': 'SOPORTE',
              'titulo': 'Ana Ruiz',
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

    testWidgets('una privada muestra el rol de la otra persona', (
      tester,
    ) async {
      await tester.pumpWidget(
        conSesion(
          'Admin',
          conversaciones: [
            {
              'id': 8,
              'tipo': 'PRIVADA',
              'titulo': 'Luis Benítez',
              'rolContraparte': 'Repartidor',
              'noLeidos': 0,
              'ultimoMensaje': {'contenido': 'Salgo con las entregas'},
            },
          ],
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('Luis Benítez'), findsOneWidget);
      expect(find.text('Repartidor'), findsOneWidget);
    });
  });
}
