import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:techstore_mobile/app/app_theme.dart';
import 'package:techstore_mobile/core/di/providers.dart';
import 'package:techstore_mobile/core/native/biometric_gate.dart';
import 'package:techstore_mobile/core/storage/secure_store.dart';

void main() {
  // La pantalla de bloqueo se dibuja en el `builder` del MaterialApp, por
  // encima del Navigator: ahí no hay ningún Material puesto por el framework.
  // Sin uno propio, Flutter pinta el texto en rojo con subrayado amarillo y esa
  // era la primera pantalla que veía la persona al abrir la app.
  testWidgets('la pantalla de bloqueo se dibuja sobre un Material', (
    tester,
  ) async {
    await tester.pumpWidget(
      ProviderScope(
        overrides: [secureStoreProvider.overrideWithValue(_EmptySecureStore())],
        child: const MaterialApp(home: BiometricGate(child: SizedBox.shrink())),
      ),
    );

    final aviso = find.text('Comprobando seguridad');
    expect(aviso, findsOneWidget);
    expect(
      find.ancestor(of: aviso, matching: find.byType(Material)),
      findsAtLeastNWidgets(1),
    );

    await tester.pumpAndSettle();
  });

  // El mensaje de error de un campo entra en una sola línea por omisión y
  // Flutter lo corta con puntos suspensivos; «Escribí un correo válido, como
  // nombre@correo.com» se leía «…como nombre…», justo sin el ejemplo.
  test('los errores de los campos pueden ocupar varias líneas', () {
    final tema = AppTheme.marca();
    expect(tema.inputDecorationTheme.errorMaxLines, greaterThan(1));
    expect(tema.inputDecorationTheme.helperMaxLines, greaterThan(1));
  });
}

final class _EmptySecureStore implements SecureStore {
  @override
  Future<void> clear() async {}

  @override
  Future<void> delete(String key) async {}

  @override
  Future<String?> read(String key) async => null;

  @override
  Future<void> write(String key, String value) async {}
}
