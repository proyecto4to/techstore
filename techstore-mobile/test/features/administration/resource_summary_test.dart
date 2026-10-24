import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:intl/date_symbol_data_local.dart';
import 'package:techstore_mobile/features/administration/domain/resource_definition.dart';
import 'package:techstore_mobile/features/administration/presentation/resource_page.dart';

void main() {
  setUpAll(() async {
    await initializeDateFormatting('es_PY');
  });

  testWidgets('los resúmenes muestran fecha, moneda y tarjetas separadas', (
    tester,
  ) async {
    final definition = resources['all-orders']!;
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          resourceProvider(definition).overrideWith(
            (ref) async => const {
              'content': [
                {
                  'id': 78,
                  'numero': 78,
                  'fecha': '2026-10-19T22:32:26.390913',
                  'total': 140000,
                  'moneda': 'PYG',
                },
                {
                  'id': 79,
                  'numero': 79,
                  'fecha': '2026-10-20T10:15:00',
                  'total': 140.50,
                  'moneda': 'USD',
                },
              ],
            },
          ),
        ],
        child: MaterialApp(
          theme: ThemeData(
            cardTheme: const CardThemeData(margin: EdgeInsets.zero),
          ),
          home: ResourcePage(definition: definition),
        ),
      ),
    );
    await tester.pumpAndSettle();

    final summaries = tester
        .widgetList<Text>(find.textContaining('Fecha:'))
        .map((text) => text.data!)
        .toList();
    expect(summaries, hasLength(2));
    expect(summaries[0], contains(RegExp(r'Fecha: .*2026.*22:32')));
    expect(summaries[0], contains(RegExp(r'Total: 140\.000\s*₲')));
    expect(summaries[1], contains(RegExp(r'Fecha: .*2026.*10:15')));
    expect(summaries[1], contains(RegExp(r'Total: 140,50\s*USD')));
    expect(find.textContaining('2026-10-19T'), findsNothing);

    final cards = find.byType(Card);
    expect(cards, findsNWidgets(2));
    final first = tester.getRect(cards.at(0));
    final second = tester.getRect(cards.at(1));
    expect(second.top - first.bottom, closeTo(10, 0.1));
    expect(tester.takeException(), isNull);
  });

  testWidgets('los pedidos del mostrador muestran su estado y se nombran al '
      'confirmar o cobrar', (tester) async {
    final definition = resources['all-orders']!;
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          resourceProvider(definition).overrideWith(
            (ref) async => const {
              'content': [
                {
                  'id': 5,
                  'numero': 5,
                  'estado': 'P',
                  'fecha': '2026-10-21T18:36:33',
                  'total': 100000,
                  'moneda': 'PYG',
                },
                {
                  'id': 1,
                  'numero': 1,
                  'estado': 'X',
                  'fecha': '2026-10-21T18:35:00',
                  'total': 240000,
                  'moneda': 'PYG',
                },
              ],
            },
          ),
        ],
        child: MaterialApp(home: ResourcePage(definition: definition)),
      ),
    );
    await tester.pumpAndSettle();

    // Antes el título era «5» y el estado no aparecía: un pedido cancelado se
    // veía igual que uno por confirmar.
    expect(find.text('Pedido N.º 5'), findsOneWidget);
    expect(find.text('Pendiente de confirmación'), findsOneWidget);
    expect(find.text('Cancelado'), findsOneWidget);

    await tester.tap(find.text('Pedido N.º 5'));
    await tester.pumpAndSettle();
    expect(find.text('Pendiente de confirmación'), findsNWidgets(2));
    expect(find.text('P'), findsNothing);

    await tester.tap(find.byType(PopupMenuButton<String>).first);
    await tester.pumpAndSettle();
    await tester.tap(find.text('Confirmar'));
    await tester.pumpAndSettle();
    expect(
      find.textContaining(RegExp(r'^Pedido N\.º 5 por 100\.000\s*₲\.$')),
      findsOneWidget,
    );
    await tester.tap(find.text('Volver'));
    await tester.pumpAndSettle();

    await tester.tap(find.byType(PopupMenuButton<String>).first);
    await tester.pumpAndSettle();
    await tester.tap(find.text('Registrar pago'));
    await tester.pumpAndSettle();
    expect(find.widgetWithText(TextFormField, '100.000'), findsOneWidget);
    await tester.tap(find.text('Cancelar'));
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
  });
}
