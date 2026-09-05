import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:techstore_mobile/core/di/providers.dart';
import 'package:techstore_mobile/features/chat/presentation/new_conversation_page.dart';

import '../../support/sesion_de_prueba.dart';

/// Elegir con quién hablar, según el rol de quien mira.
///
/// Lo que se comprueba acá es la pantalla. Que un cajero no pueda escribirle a
/// un cliente lo garantiza el servidor —hay un test de integración para eso—;
/// esta capa se limita a no ofrecer una elección que después va a ser
/// rechazada.
void main() {
  Widget conRol(
    String rol, {
    List<Map<String, Object?>> destinatarios = const [],
  }) {
    return ProviderScope(
      overrides: [
        apiClientProvider.overrideWithValue(apiClientDeRol(rol)),
        secureStoreProvider.overrideWithValue(StoreConSesion()),
        destinatariosProvider.overrideWith(
          (ref, busqueda) async => destinatarios,
        ),
      ],
      child: const MaterialApp(home: NewConversationPage()),
    );
  }

  testWidgets('administración puede filtrar por cada rol', (tester) async {
    await tester.pumpWidget(conRol('Admin'));
    await tester.pumpAndSettle();

    expect(find.text('Administradores'), findsOneWidget);
    expect(find.text('Cajeros'), findsOneWidget);
    expect(find.text('Repartidores'), findsOneWidget);
    expect(find.text('Clientes'), findsOneWidget);
  });

  testWidgets('al cajero no le ofrecen filtros que no puede usar', (
    tester,
  ) async {
    // Su única opción es administración: unos chips que no cambian nada solo
    // sugieren que podría escribirle a un cliente.
    await tester.pumpWidget(conRol('Cajero'));
    await tester.pumpAndSettle();

    expect(find.text('Clientes'), findsNothing);
    expect(find.text('Todos'), findsNothing);
  });

  testWidgets('al repartidor tampoco', (tester) async {
    await tester.pumpWidget(conRol('Repartidor'));
    await tester.pumpAndSettle();

    expect(find.text('Repartidores'), findsNothing);
  });

  testWidgets('cada persona se muestra con su rol', (tester) async {
    await tester.pumpWidget(
      conRol(
        'Admin',
        destinatarios: [
          {
            'id': 4,
            'nombreCompleto': 'Ana Ruiz',
            'email': 'ana@techstore.py',
            'rol': 'Cajero',
            'conversacionId': null,
          },
        ],
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Ana Ruiz'), findsOneWidget);
    expect(find.text('ana@techstore.py'), findsOneWidget);
  });

  testWidgets('avisa cuándo ya existe una conversación con esa persona', (
    tester,
  ) async {
    // Sin este aviso, tocar dos veces al mismo contacto parece abrir chats
    // distintos, cuando en realidad el servidor devuelve siempre el mismo.
    await tester.pumpWidget(
      conRol(
        'Admin',
        destinatarios: [
          {
            'id': 4,
            'nombreCompleto': 'Ana Ruiz',
            'email': 'ana@techstore.py',
            'rol': 'Cajero',
            'conversacionId': 12,
          },
        ],
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Ya hablaron'), findsOneWidget);
  });

  testWidgets('sin resultados explica qué hacer', (tester) async {
    await tester.pumpWidget(conRol('Admin'));
    await tester.pumpAndSettle();

    expect(find.text('No hay personas disponibles'), findsOneWidget);
  });
}
