import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:techstore_mobile/core/di/providers.dart';
import 'package:techstore_mobile/features/chat/presentation/new_conversation_page.dart';

import 'sesion_de_prueba.dart';

/// La pantalla de elegir destinatario en distintos tamaños de pantalla.
///
/// El caso que importa es el teléfono angosto con los cuatro filtros de rol:
/// ahí es donde una fila de chips se desborda y Flutter pinta la franja de
/// «overflow». Los chips van en una lista horizontal justamente por eso, y
/// estos casos lo dejan fijado.
void main() {
  final destinatarios = [
    for (var i = 1; i <= 8; i++)
      {
        'id': i,
        'nombreCompleto': 'Persona Con Nombre Largo Número $i',
        'email': 'persona.con.correo.largo.$i@techstore.py',
        'rol': 'Cajero',
        'conversacionId': i.isEven ? i : null,
      },
  ];

  Widget pantalla() => ProviderScope(
    overrides: [
      apiClientProvider.overrideWithValue(apiClientDeRol('Admin')),
      secureStoreProvider.overrideWithValue(StoreConSesion()),
      destinatariosProvider.overrideWith((ref, busqueda) async => destinatarios),
    ],
    child: const MaterialApp(home: NewConversationPage()),
  );

  Future<void> enPantallaDe(WidgetTester tester, Size tamano) async {
    tester.view.physicalSize = tamano;
    tester.view.devicePixelRatio = 1.0;
    addTearDown(tester.view.reset);
    await tester.pumpWidget(pantalla());
    await tester.pumpAndSettle();
  }

  testWidgets('en un teléfono chico no se desborda nada', (tester) async {
    // 320x568 es lo más angosto que se ve todavía en equipos en uso.
    await enPantallaDe(tester, const Size(320, 568));

    expect(tester.takeException(), isNull);
    expect(find.text('Nueva conversación'), findsOneWidget);
  });

  testWidgets('en un teléfono común tampoco', (tester) async {
    await enPantallaDe(tester, const Size(411, 891));

    expect(tester.takeException(), isNull);
  });

  testWidgets('en una tablet la lista sigue entrando', (tester) async {
    await enPantallaDe(tester, const Size(1024, 1366));

    expect(tester.takeException(), isNull);
    expect(find.textContaining('Persona Con Nombre Largo'), findsWidgets);
  });

  testWidgets('un nombre largo se corta con puntos, no se desborda', (
    tester,
  ) async {
    await enPantallaDe(tester, const Size(320, 568));

    // El correo va con ellipsis: si se desbordara, la fila pintaría la franja
    // amarilla y `takeException` lo delataría.
    final correo = tester.widget<Text>(
      find.text('persona.con.correo.largo.1@techstore.py'),
    );
    expect(correo.overflow, TextOverflow.ellipsis);
    expect(tester.takeException(), isNull);
  });
}
