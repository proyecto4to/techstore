import 'package:drift/native.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:techstore_mobile/app/environment.dart';
import 'package:techstore_mobile/core/animacion/vuelo_al_carrito.dart';
import 'package:techstore_mobile/core/database/app_database.dart';
import 'package:techstore_mobile/core/di/providers.dart';
import 'package:techstore_mobile/core/storage/secure_store.dart';
import 'package:techstore_mobile/features/cart/presentation/boton_carrito.dart';

final class _SinSesion implements SecureStore {
  @override
  Future<void> clear() async {}

  @override
  Future<void> delete(String key) async {}

  @override
  Future<String?> read(String key) async => null;

  @override
  Future<void> write(String key, String value) async {}
}

Widget _tienda({bool sinAnimaciones = false}) => ProviderScope(
  overrides: [
    environmentProvider.overrideWithValue(AppEnvironment.development()),
    databaseProvider.overrideWithValue(AppDatabase(NativeDatabase.memory())),
    secureStoreProvider.overrideWithValue(_SinSesion()),
  ],
  child: MaterialApp(
    builder: (context, hijo) => MediaQuery(
      data: MediaQuery.of(context).copyWith(disableAnimations: sinAnimaciones),
      child: hijo!,
    ),
    home: Scaffold(
      appBar: AppBar(actions: const [BotonCarrito()]),
      body: Builder(
        builder: (context) => Center(
          child: ElevatedButton(
            onPressed: () => lanzarVueloAlCarrito(
              context,
              desde: const Rect.fromLTWH(100, 400, 40, 40),
            ),
            child: const Text('Agregar'),
          ),
        ),
      ),
    ),
  ),
);

void main() {
  testWidgets('el producto vuela hasta el carrito de la barra y desaparece al llegar', (
    tester,
  ) async {
    await tester.pumpWidget(_tienda());
    await tester.pumpAndSettle();
    expect(find.byIcon(Icons.shopping_bag_outlined), findsOneWidget);

    await tester.tap(find.text('Agregar'));
    await tester.pump(const Duration(milliseconds: 200));
    // En pleno vuelo hay dos bolsas: la del carrito y la que viaja.
    expect(find.byIcon(Icons.shopping_bag_outlined), findsNWidgets(2));

    await tester.pumpAndSettle();
    expect(find.byIcon(Icons.shopping_bag_outlined), findsOneWidget);
  });

  testWidgets('con las animaciones quitadas no vuela nada', (tester) async {
    await tester.pumpWidget(_tienda(sinAnimaciones: true));
    await tester.pumpAndSettle();

    await tester.tap(find.text('Agregar'));
    await tester.pump(const Duration(milliseconds: 200));
    expect(find.byIcon(Icons.shopping_bag_outlined), findsOneWidget);
  });

  testWidgets('un carrito tapado por otra pantalla no recibe el vuelo', (
    tester,
  ) async {
    await tester.pumpWidget(_tienda());
    await tester.pumpAndSettle();
    expect(DestinosDelCarrito.visible, isNotNull);

    final navegador = tester.state<NavigatorState>(find.byType(Navigator));
    navegador.push(
      MaterialPageRoute<void>(builder: (_) => const Scaffold(body: SizedBox())),
    );
    await tester.pumpAndSettle();

    // La primera pantalla sigue montada debajo, con su carrito, pero no se ve.
    expect(DestinosDelCarrito.visible, isNull);
  });
}
