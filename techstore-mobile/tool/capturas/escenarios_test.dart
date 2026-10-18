import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:techstore_mobile/features/catalog/presentation/vitrina/escenario_de_producto.dart';

import 'banco.dart';

void main() {
  // Fuera de testWidgets: adentro corre en tiempo falso y la carga de
  // fuentes, que espera al motor, no termina nunca.
  setUpAll(() async {
    if (hayBanco) await cargarFuentes();
  });

  testWidgets('escenarios: tarjetas de todo el catálogo', (tester) async {
    if (!hayBanco) return;
    final productos = productosReales();
    final contenedor = contenedorDeBanco();
    addTearDown(contenedor.dispose);
    await prepararFotos(tester, contenedor, productos);

    await montar(
      tester,
      contenedor,
      Scaffold(
        body: GridView.count(
          crossAxisCount: 3,
          padding: const EdgeInsets.all(8),
          mainAxisSpacing: 8,
          crossAxisSpacing: 8,
          children: [
            for (final p in productos)
              ClipRRect(
                borderRadius: BorderRadius.circular(16),
                child: EscenarioDeProducto(producto: p),
              ),
          ],
        ),
      ),
      alto: 1700,
    );
    await esperarImagenes(tester);
    await expectLater(
      find.byType(GridView),
      matchesGoldenFile('salida/escenarios-tarjeta.png'),
    );
  }, timeout: const Timeout(Duration(minutes: 3)));

  testWidgets('escenarios: fichas', (tester) async {
    if (!hayBanco) return;
    final productos = productosReales()
        .where((p) => const [2, 33, 8, 36, 20, 10].contains(p.id))
        .toList();
    final contenedor = contenedorDeBanco();
    addTearDown(contenedor.dispose);
    await prepararFotos(tester, contenedor, productos);

    await montar(
      tester,
      contenedor,
      Scaffold(
        body: ListView(
          children: [
            for (final p in productos)
              SizedBox(
                height: 360,
                child: EscenarioDeProducto(
                  producto: p,
                  encuadre: Encuadre.ficha,
                ),
              ),
          ],
        ),
      ),
      alto: 2200,
    );
    await esperarImagenes(tester);
    await expectLater(
      find.byType(ListView),
      matchesGoldenFile('salida/escenarios-ficha.png'),
    );
  }, timeout: const Timeout(Duration(minutes: 3)));
}
