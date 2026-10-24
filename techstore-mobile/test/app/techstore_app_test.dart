import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:techstore_mobile/app/environment.dart';
import 'package:techstore_mobile/app/techstore_app.dart';
import 'package:techstore_mobile/core/di/providers.dart';
import 'package:techstore_mobile/core/storage/secure_store.dart';

void main() {
  testWidgets('restaura sesión y redirige al login cuando no existe', (
    tester,
  ) async {
    final environment = AppEnvironment.development();
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          environmentProvider.overrideWithValue(environment),
          secureStoreProvider.overrideWithValue(_EmptySecureStore()),
        ],
        child: TechStoreApp(environment: environment),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('login-background')), findsOneWidget);
    expect(find.text('Iniciar sesión'), findsOneWidget);
    expect(find.text('Olvidé mi contraseña'), findsOneWidget);
    final context = tester.element(find.text('Iniciar sesión'));
    expect(Localizations.localeOf(context).languageCode, 'es');
    expect(MaterialLocalizations.of(context).backButtonTooltip, 'Atrás');
  });

  for (final (enlace, titulo) in const [
    ('Crear una cuenta', 'Crear cuenta'),
    ('Olvidé mi contraseña', 'Recuperar contraseña'),
  ]) {
    testWidgets('«$enlace» se abre encima del ingreso y «atrás» vuelve a él', (
      tester,
    ) async {
      final environment = AppEnvironment.development();
      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            environmentProvider.overrideWithValue(environment),
            secureStoreProvider.overrideWithValue(_EmptySecureStore()),
          ],
          child: TechStoreApp(environment: environment),
        ),
      );
      await tester.pumpAndSettle();

      await tester.ensureVisible(find.text(enlace));
      await tester.tap(find.text(enlace));
      await tester.pumpAndSettle();
      expect(find.text(titulo), findsWidgets);

      // Antes se reemplazaba la pila entera: no había flecha, y el «atrás»
      // del teléfono cerraba la app.
      await tester.tap(find.byType(BackButton));
      await tester.pumpAndSettle();
      expect(find.byKey(const Key('login-background')), findsOneWidget);
    });
  }
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
