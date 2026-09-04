import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

/// El margen de la barra inferior del sistema se resuelve una sola vez, en el
/// `builder` del MaterialApp. Estos casos fijan las dos propiedades de las que
/// depende ese arreglo, porque si alguna no se cumpliera habría que volver a
/// tocar pantalla por pantalla.
void main() {
  const barraDelSistema = EdgeInsets.only(bottom: 48);

  double alto(WidgetTester tester) =>
      tester.view.physicalSize.height / tester.view.devicePixelRatio;

  Widget conBarra(Widget hijo) => MediaQuery(
    data: const MediaQueryData(
      padding: barraDelSistema,
      viewPadding: barraDelSistema,
    ),
    child: Directionality(textDirection: TextDirection.ltr, child: hijo),
  );

  testWidgets('el contenido no llega hasta el borde tapado por la barra', (
    tester,
  ) async {
    await tester.pumpWidget(
      conBarra(
        SafeArea(
          top: false,
          child: Column(
            children: [
              const Spacer(),
              Container(key: const Key('pie'), height: 40, color: Colors.blue),
            ],
          ),
        ),
      ),
    );

    final pie = tester.getRect(find.byKey(const Key('pie')));
    expect(
      pie.bottom,
      lessThanOrEqualTo(alto(tester) - barraDelSistema.bottom),
      reason: 'lo último de la pantalla quedaría debajo de la barra',
    );
  });

  testWidgets('un SafeArea de adentro no vuelve a agregar el margen', (
    tester,
  ) async {
    // De esto depende no tener que sacar los SafeArea que ya tenían el carrito
    // y el checkout: el de afuera consume el margen del MediaQuery, así que los
    // de adentro no suman nada y no queda un hueco doble.
    await tester.pumpWidget(
      conBarra(
        SafeArea(
          top: false,
          child: Column(
            children: [
              const Spacer(),
              SafeArea(
                child: Container(
                  key: const Key('pie-anidado'),
                  height: 40,
                  color: Colors.green,
                ),
              ),
            ],
          ),
        ),
      ),
    );

    final pie = tester.getRect(find.byKey(const Key('pie-anidado')));
    expect(
      pie.bottom,
      alto(tester) - barraDelSistema.bottom,
      reason: 'el margen se aplicó dos veces y quedó un hueco de más',
    );
  });

  testWidgets('con el teclado arriba no sobra espacio', (tester) async {
    // Mientras el teclado está visible el sistema deja de reportar el margen de
    // la barra, así que el SafeArea no debe empujar nada.
    await tester.pumpWidget(
      MediaQuery(
        data: const MediaQueryData(
          padding: EdgeInsets.zero,
          viewInsets: EdgeInsets.only(bottom: 300),
        ),
        child: const Directionality(
          textDirection: TextDirection.ltr,
          child: SafeArea(
            top: false,
            child: Column(
              children: [
                Spacer(),
                SizedBox(key: Key('campo'), height: 40),
              ],
            ),
          ),
        ),
      ),
    );

    final campo = tester.getRect(find.byKey(const Key('campo')));
    expect(
      campo.bottom,
      alto(tester),
      reason: 'el SafeArea agregó margen de más',
    );
  });
}
