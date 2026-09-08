import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:techstore_mobile/app/home_page.dart';
import 'package:techstore_mobile/core/di/providers.dart';
import 'package:techstore_mobile/features/auth/application/session_controller.dart';

import '../support/sesion_de_prueba.dart';

/// El contador de la campana en el inicio.
///
/// No se falsea el provider: el caso contesta el endpoint real para que el
/// nombre del campo —`noLeidas`— quede comprobado. Si el servidor lo renombra,
/// el número se queda en cero para siempre y no lo nota nadie; acá falla.
void main() {
  Widget inicioCon(Object? respuestaDelContador) => ProviderScope(
    overrides: [
      apiClientProvider.overrideWithValue(
        apiClientDeRol(
          'Cliente',
          respuestas: {'no-leidas': respuestaDelContador},
        ),
      ),
      secureStoreProvider.overrideWithValue(StoreConSesion()),
    ],
    // El inicio da por hecho que hay sesión; en la app eso lo garantiza el
    // router. Acá se espera a que el controlador termine de restaurarla.
    child: MaterialApp(
      home: Consumer(
        builder: (context, ref, _) =>
            ref.watch(authSessionProvider).isAuthenticated
            ? const HomePage(environmentLabel: null)
            : const SizedBox.shrink(),
      ),
    ),
  );

  testWidgets('sin avisos pendientes la campana no muestra ningún número', (
    tester,
  ) async {
    final handle = tester.ensureSemantics();
    await tester.pumpWidget(inicioCon({'noLeidas': 0}));
    await tester.pumpAndSettle();

    expect(find.text('Notificaciones'), findsOneWidget);
    expect(find.text('0'), findsNothing);
    // Exactamente eso: sin el nombre repetido, que es como se escuchaba antes.
    expect(
      tester.getSemantics(find.text('Notificaciones')).label,
      'Notificaciones',
    );
    handle.dispose();
  });

  testWidgets('con avisos pendientes muestra cuántos y también lo dice', (
    tester,
  ) async {
    final handle = tester.ensureSemantics();
    await tester.pumpWidget(inicioCon({'noLeidas': 3}));
    await tester.pumpAndSettle();

    expect(find.text('3'), findsOneWidget);
    expect(
      tester.getSemantics(find.text('Notificaciones')).label,
      'Notificaciones, 3 sin leer',
    );
    handle.dispose();
  });

  testWidgets('un número largo no desborda la baldosa', (tester) async {
    await tester.pumpWidget(inicioCon({'noLeidas': 150}));
    await tester.pumpAndSettle();

    expect(find.text('99+'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });
}
