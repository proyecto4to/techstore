import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:techstore_mobile/core/widgets/visor_de_documento.dart';

/// El visor de documentos.
///
/// Lo que se comprueba acá es justo lo que antes no se podía hacer: acercarse a
/// leer un número y correr la hoja para ver una esquina. La vista previa
/// anterior encajaba la hoja en el alto disponible y ahí terminaba.
void main() {
  /// Un PNG de un punto. Alcanza: lo que se prueba es el comportamiento del
  /// visor, no el dibujo de la página.
  final unPunto = base64Decode(
    'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAE'
    'hQGAhKmMIQAAAABJRU5ErkJggg==',
  );

  List<PaginaDeDocumento> hojas(int cuantas) => [
    for (var i = 0; i < cuantas; i++)
      PaginaDeDocumento(imagen: unPunto, ancho: 1240, alto: 1754),
  ];

  Widget visor(int paginas) => MaterialApp(
    home: Scaffold(body: VisorDeDocumento(paginas: hojas(paginas))),
  );

  InteractiveViewer marcoDe(WidgetTester tester) =>
      tester.widget<InteractiveViewer>(find.byType(InteractiveViewer).first);

  testWidgets('la página tiene papel blanco aun con tema oscuro', (
    tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(
        theme: ThemeData.dark(),
        home: Scaffold(body: VisorDeDocumento(paginas: hojas(1))),
      ),
    );
    final papel = find
        .ancestor(of: find.byType(Image), matching: find.byType(ColoredBox))
        .first;
    expect(tester.widget<ColoredBox>(papel).color, Colors.white);
  });

  testWidgets('la hoja no queda presa del tamaño del marco', (tester) async {
    await tester.pumpWidget(visor(1));

    expect(
      marcoDe(tester).boundaryMargin,
      const EdgeInsets.all(double.infinity),
      reason:
          'con un límite, acercada la hoja se topa con el borde y no se llega '
          'a la esquina de la página',
    );
    expect(marcoDe(tester).maxScale, greaterThan(1));
  });

  testWidgets('empieza mostrando la hoja entera, sin acercar', (tester) async {
    await tester.pumpWidget(visor(1));

    expect(find.text('100%'), findsOneWidget);
    expect(find.text('1 página'), findsOneWidget);
    expect(
      marcoDe(tester).panEnabled,
      isFalse,
      reason:
          'sin acercar la hoja entra entera, así que arrastrar solo serviría '
          'para tirarla fuera de la pantalla',
    );
  });

  testWidgets('acercar cambia el porcentaje y habilita arrastrar', (
    tester,
  ) async {
    await tester.pumpWidget(visor(1));

    await tester.tap(find.byTooltip('Acercar'));
    await tester.pump();

    expect(find.text('100%'), findsNothing);
    expect(find.text('160%'), findsOneWidget);
    expect(marcoDe(tester).panEnabled, isTrue);
  });

  testWidgets('«ver la hoja entera» vuelve al principio', (tester) async {
    await tester.pumpWidget(visor(1));
    await tester.tap(find.byTooltip('Acercar'));
    await tester.pump();

    await tester.tap(find.byTooltip('Ver la hoja entera'));
    await tester.pump();

    expect(find.text('100%'), findsOneWidget);
    expect(marcoDe(tester).panEnabled, isFalse);
  });

  testWidgets('no se aleja más allá de la hoja entera', (tester) async {
    await tester.pumpWidget(visor(1));

    expect(
      tester
          .widget<IconButton>(find.widgetWithIcon(IconButton, Icons.zoom_out))
          .onPressed,
      isNull,
      reason: 'alejar de la hoja entera solo agrega aire alrededor',
    );
  });

  group('varias páginas', () {
    testWidgets('se dice en cuál se está y se pasa a la siguiente', (
      tester,
    ) async {
      await tester.pumpWidget(visor(3));

      expect(find.text('1 de 3'), findsOneWidget);
      expect(
        tester
            .widget<IconButton>(
              find.widgetWithIcon(IconButton, Icons.chevron_left),
            )
            .onPressed,
        isNull,
      );

      await tester.tap(find.byTooltip('Página siguiente'));
      await tester.pumpAndSettle();

      expect(find.text('2 de 3'), findsOneWidget);
    });

    testWidgets('la última página no ofrece una siguiente', (tester) async {
      await tester.pumpWidget(visor(2));

      await tester.tap(find.byTooltip('Página siguiente'));
      await tester.pumpAndSettle();

      expect(find.text('2 de 2'), findsOneWidget);
      expect(
        tester
            .widget<IconButton>(
              find.widgetWithIcon(IconButton, Icons.chevron_right),
            )
            .onPressed,
        isNull,
      );
    });

    testWidgets('cambiar de hoja la muestra entera de nuevo', (tester) async {
      await tester.pumpWidget(visor(2));
      await tester.tap(find.byTooltip('Acercar'));
      await tester.pump();
      expect(find.text('160%'), findsOneWidget);

      await tester.tap(find.byTooltip('Página siguiente'));
      await tester.pumpAndSettle();

      expect(
        find.text('100%'),
        findsOneWidget,
        reason:
            'llegar a la hoja siguiente acercado y corrido a una esquina deja '
            'mirando un pedazo de blanco sin saber dónde se está',
      );
    });
  });
}
