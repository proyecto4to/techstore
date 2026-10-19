import 'package:drift/native.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:techstore_mobile/app/environment.dart';
import 'package:techstore_mobile/core/database/app_database.dart';
import 'package:techstore_mobile/core/di/providers.dart';
import 'package:techstore_mobile/features/cart/presentation/agregar_al_carrito.dart';
import 'package:techstore_mobile/features/catalog/domain/product.dart';

const _producto = Product(
  id: 7,
  name: 'ASUS ROG Strix B650-A',
  price: 1850000,
  currency: 'PYG',
  available: true,
  maxPerOrder: 5,
);

Future<void> agregar(WidgetTester tester, {required bool lector}) async {
  final base = AppDatabase(NativeDatabase.memory());
  addTearDown(base.close);
  await tester.pumpWidget(
    ProviderScope(
      overrides: [
        environmentProvider.overrideWithValue(AppEnvironment.development()),
        databaseProvider.overrideWithValue(base),
      ],
      child: MaterialApp(
        builder: (context, hijo) => MediaQuery(
          data: MediaQuery.of(context).copyWith(accessibleNavigation: lector),
          child: hijo!,
        ),
        home: Scaffold(
          body: Consumer(
            builder: (context, ref, _) => Center(
              child: TextButton(
                onPressed: () => agregarAlCarrito(context, ref, _producto),
                child: const Text('Agregar'),
              ),
            ),
          ),
        ),
      ),
    ),
  );
  await tester.tap(find.text('Agregar'));
  await tester.pump();
  await tester.pump(const Duration(milliseconds: 400));
  expect(find.text('Ver carrito'), findsOneWidget);
}

void main() {
  testWidgets('el aviso de «Agregado al carrito» se va solo', (tester) async {
    await agregar(tester, lector: false);
    // Flutter deja fijo un aviso con acción salvo que se le diga lo
    // contrario: antes quedaba minutos tapando los productos.
    await tester.pump(const Duration(seconds: 3));
    await tester.pumpAndSettle();
    expect(find.text('Ver carrito'), findsNothing);
  });

  testWidgets('con lector de pantalla el aviso espera a que se lo use', (
    tester,
  ) async {
    await agregar(tester, lector: true);
    await tester.pump(const Duration(seconds: 10));
    await tester.pumpAndSettle();
    expect(find.text('Ver carrito'), findsOneWidget);
  });
}
