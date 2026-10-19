import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:techstore_mobile/core/animacion/aparicion.dart';
import 'package:techstore_mobile/core/diseno/movimiento.dart';
import 'package:techstore_mobile/core/widgets/async_states.dart';
import 'package:techstore_mobile/core/widgets/marca/isotipo.dart';

/// Monta [hijo] con o sin la opción del sistema «quitar animaciones».
Future<void> montar(
  WidgetTester tester,
  Widget hijo, {
  required bool reducido,
}) => tester.pumpWidget(
  MaterialApp(
    home: Builder(
      builder: (context) => MediaQuery(
        data: MediaQuery.of(context).copyWith(disableAnimations: reducido),
        child: Scaffold(body: Center(child: hijo)),
      ),
    ),
  ),
);

void main() {
  group('con «quitar animaciones» activado', () {
    testWidgets('una entrada aparece entera en el primer cuadro', (
      tester,
    ) async {
      await montar(
        tester,
        const Aparicion(child: Text('Producto')),
        reducido: true,
      );
      final fundido = tester.widget<FadeTransition>(
        find
            .ancestor(
              of: find.text('Producto'),
              matching: find.byType(FadeTransition),
            )
            .first,
      );
      expect(fundido.opacity.value, 1);
      expect(tester.hasRunningAnimations, isFalse);
    });

    testWidgets('el texto que se escribe solo ya está escrito', (
      tester,
    ) async {
      await montar(
        tester,
        TextoTecleado('Precio claro. Stock real.', estilo: const TextStyle()),
        reducido: true,
      );
      expect(tester.hasRunningAnimations, isFalse);
    });

    testWidgets('el indicador de carga se queda quieto', (tester) async {
      await montar(tester, const AppLoading(), reducido: true);
      // Si el cursor siguiera latiendo, esto no terminaría nunca.
      await tester.pumpAndSettle();
      expect(tester.hasRunningAnimations, isFalse);
    });

    testWidgets('el paso entre pantallas es solo un fundido', (tester) async {
      final ruta = MaterialPageRoute<void>(builder: (_) => const SizedBox());
      await tester.pumpWidget(
        MediaQuery(
          data: const MediaQueryData(disableAnimations: true),
          child: Builder(
            builder: (context) => const TransicionDeMarca().buildTransitions(
              ruta,
              context,
              kAlwaysCompleteAnimation,
              kAlwaysDismissedAnimation,
              const SizedBox(),
            ),
          ),
        ),
      );
      expect(find.byType(FadeTransition), findsOneWidget);
      expect(find.byType(SlideTransition), findsNothing);
      expect(find.byType(ScaleTransition), findsNothing);
    });
  });

  group('sin restricciones', () {
    testWidgets('nada queda en bucle: las entradas terminan solas', (
      tester,
    ) async {
      await montar(
        tester,
        Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Aparicion(orden: 3, child: Text('Producto')),
            TextoTecleado('Precio claro.', estilo: const TextStyle()),
            const IsotipoAnimado(),
          ],
        ),
        reducido: false,
      );
      expect(tester.hasRunningAnimations, isTrue);
      await tester.pumpAndSettle();
      expect(tester.hasRunningAnimations, isFalse);
    });

    testWidgets('el indicador de carga late mientras dura la espera', (
      tester,
    ) async {
      await montar(tester, const AppLoading(), reducido: false);
      await tester.pump(const Duration(seconds: 3));
      expect(tester.hasRunningAnimations, isTrue);
      // Al irse la carga, el latido muere con ella.
      await tester.pumpWidget(const SizedBox());
      expect(tester.hasRunningAnimations, isFalse);
    });
  });
}
