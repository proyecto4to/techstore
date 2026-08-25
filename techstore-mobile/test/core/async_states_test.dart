import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:techstore_mobile/core/widgets/async_states.dart';

void main() {
  testWidgets('estados async exponen semántica y reintento accesible', (
    tester,
  ) async {
    final semantics = tester.ensureSemantics();
    var retried = false;
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: AppErrorState(
            message: 'No se pudo cargar',
            onRetry: () => retried = true,
          ),
        ),
      ),
    );

    expect(find.bySemanticsLabel(RegExp('No se pudo cargar')), findsOneWidget);
    await tester.tap(find.widgetWithText(FilledButton, 'Reintentar'));
    expect(retried, isTrue);
    semantics.dispose();
  });

  testWidgets('empty state tolera texto grande sin overflow', (tester) async {
    final semantics = tester.ensureSemantics();
    await tester.pumpWidget(
      MaterialApp(
        home: MediaQuery(
          data: const MediaQueryData(
            size: Size(320, 480),
            textScaler: TextScaler.linear(2),
          ),
          child: const Scaffold(
            body: AppEmptyState(
              message: 'No hay resultados para los filtros seleccionados',
            ),
          ),
        ),
      ),
    );

    expect(tester.takeException(), isNull);
    expect(
      find.bySemanticsLabel(
        RegExp('No hay resultados para los filtros seleccionados'),
      ),
      findsOneWidget,
    );
    semantics.dispose();
  });
}
