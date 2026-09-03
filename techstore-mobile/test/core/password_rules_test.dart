import 'package:flutter_test/flutter_test.dart';
import 'package:techstore_mobile/core/format/password_rules.dart';

/// La regla tiene que coincidir con la que aplica el backend, y el motivo que
/// se le muestra a la persona tiene que decir qué le falta a *su* contraseña.
void main() {
  test('acepta una contraseña que cumple los cuatro requisitos', () {
    expect(passwordValida('Segura123'), isTrue);
    expect(errorDePassword('Segura123'), isNull);
  });

  test('no dice nada mientras el campo está vacío', () {
    // Marcar el campo en rojo antes de que escriba una letra es ruido.
    expect(errorDePassword(''), isNull);
  });

  test('explica exactamente qué falta, no un rechazo genérico', () {
    expect(errorDePassword('Ab1'), contains('caracteres'));
    expect(errorDePassword('segura123'), contains('mayúscula'));
    expect(errorDePassword('SEGURA123'), contains('minúscula'));
    expect(errorDePassword('SeguraClave'), contains('número'));
  });

  test('rechaza lo que el servidor también rechazaría', () {
    for (final debil in ['corta1A', 'sinnumeros', 'SINMINUSCULAS1', '12345678']) {
      expect(passwordValida(debil), isFalse, reason: 'debería rechazar "$debil"');
      expect(errorDePassword(debil), isNotNull);
    }
  });

  test('el texto de requisitos menciona el mínimo real', () {
    // Si alguien cambia el mínimo, el texto que se muestra tiene que seguirlo.
    expect(requisitosPassword, contains('$minimoCaracteres'));
  });
}
