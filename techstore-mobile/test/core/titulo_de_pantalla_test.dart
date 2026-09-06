import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:techstore_mobile/core/widgets/titulo_de_pantalla.dart';

/// El título de una pantalla se achica antes que cortarse.
///
/// Con la letra grande que muchos equipos traen configurada, «Ubicación de la
/// empresa» se leía «Ubicación de la empr…» en el Moto G42 de prueba. Perder el
/// final de un título es perder justo lo que lo distingue de otro parecido.
void main() {
  /// El ancho que el texto ocupa realmente en pantalla.
  double anchoPintado(WidgetTester tester, String texto) =>
      tester.getSize(find.text(texto)).width;

  Widget enBarra(String titulo, {required double ancho}) => MaterialApp(
    home: Scaffold(
      appBar: AppBar(
        title: SizedBox(width: ancho, child: TituloDePantalla(titulo)),
      ),
    ),
  );

  testWidgets('un título largo entra completo en un ancho chico', (
    tester,
  ) async {
    const largo = 'Ubicación de la empresa';
    await tester.pumpWidget(enBarra(largo, ancho: 120));
    await tester.pumpAndSettle();

    // El Text existe entero: no lo reemplazó una elipsis.
    expect(find.text(largo), findsOneWidget);
    // Y el FittedBox lo dejó dentro del ancho disponible.
    final caja = tester.getSize(find.byType(TituloDePantalla));
    expect(caja.width, lessThanOrEqualTo(120));
  });

  testWidgets('un título que entra no se estira para llenar el espacio', (
    tester,
  ) async {
    const corto = 'Caja';

    await tester.pumpWidget(enBarra(corto, ancho: 400));
    await tester.pumpAndSettle();
    final enCuatrocientos = anchoPintado(tester, corto);

    await tester.pumpWidget(enBarra(corto, ancho: 800));
    await tester.pumpAndSettle();
    final enOchocientos = anchoPintado(tester, corto);

    // `BoxFit.scaleDown` solo reduce: con el doble de lugar el texto se pinta
    // igual. Si escalara hacia arriba, las pantallas de título corto
    // aparecerían con la letra más grande que el resto de la aplicación.
    expect(enOchocientos, closeTo(enCuatrocientos, 0.5));
  });
}
