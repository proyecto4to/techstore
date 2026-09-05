import 'package:local_auth/local_auth.dart';

import '../errors/app_failure.dart';
import '../storage/secure_store.dart';

final class BiometricService {
  BiometricService(this._store, {LocalAuthentication? authentication})
    : _authentication = authentication ?? LocalAuthentication();

  static const _enabledKey = 'techstore.biometric_enabled';
  static const _guardKey = 'techstore.biometric_guard_operations';

  final SecureStore _store;
  final LocalAuthentication _authentication;

  Future<bool> isAvailable() async {
    try {
      return await _authentication.isDeviceSupported() &&
          await _authentication.canCheckBiometrics;
    } catch (_) {
      return false;
    }
  }

  /// Biometrías realmente inscriptas, para describir el sensor en la UI.
  Future<List<BiometricType>> enrolled() async {
    try {
      return await _authentication.getAvailableBiometrics();
    } catch (_) {
      return const [];
    }
  }

  Future<bool> isEnabled() async => await _store.read(_enabledKey) == 'true';

  /// Nombre del sensor disponible, para que la UI no hable de "huella" en un
  /// equipo que en realidad usa la cara.
  Future<String> sensorLabel() async {
    final types = await enrolled();
    if (types.contains(BiometricType.face)) return 'reconocimiento facial';
    if (types.contains(BiometricType.fingerprint)) return 'tu huella';
    if (types.contains(BiometricType.iris)) return 'lectura de iris';
    return 'la biometría del dispositivo';
  }

  Future<void> setEnabled(bool enabled) async {
    if (enabled) {
      if (!await authenticate(reason: 'Activá el acceso biométrico')) {
        throw const ValidationFailure('No pudimos validar tu identidad. Intentá de nuevo.');
      }
      await _store.write(_enabledKey, 'true');
    } else {
      await _store.delete(_enabledKey);
      await _store.delete(_guardKey);
    }
  }

  /// Si está activo, las operaciones sensibles piden una confirmación extra.
  ///
  /// Apagado por omisión: ningún flujo existente cambia hasta que el usuario
  /// lo habilita desde Mi cuenta.
  Future<bool> isOperationGuardEnabled() async =>
      await _store.read(_guardKey) == 'true';

  /// Enciende o apaga la guarda.
  ///
  /// No exige biometría ni confirma identidad por su cuenta: de eso se ocupa
  /// quien llama, que es el único con contexto para pedir la contraseña cuando
  /// el equipo no tiene sensor. Exigirla acá dejaba la opción inalcanzable
  /// justamente en los equipos donde más falta hace.
  Future<void> setOperationGuard(bool enabled) async {
    if (enabled) {
      await _store.write(_guardKey, 'true');
    } else {
      await _store.delete(_guardKey);
    }
  }

  /// Confirmación puntual antes de una operación sensible.
  ///
  /// Devuelve `true` — dejando pasar la operación — cuando la guarda está
  /// apagada o el equipo no tiene biometría, para no bloquear a quien no puede
  /// usarla. Solo cuando el usuario la activó explícitamente y el sensor
  /// responde, un rechazo detiene la operación.
  Future<bool> confirmOperation({required String reason}) async {
    if (!await isOperationGuardEnabled()) return true;
    if (!await isAvailable()) return true;
    return confirmIdentity(reason: reason);
  }

  /// Verificación con biometría admitiendo el PIN/patrón del equipo como
  /// alternativa, para que un dedo húmedo no deje al cajero trabado.
  Future<bool> confirmIdentity({required String reason}) =>
      authenticate(reason: reason, biometricOnly: false);

  Future<bool> authenticate({
    String reason = 'Desbloqueá TechStore para continuar',
    bool biometricOnly = true,
  }) async {
    try {
      return await _authentication.authenticate(
        localizedReason: reason,
        biometricOnly: biometricOnly,
        persistAcrossBackgrounding: true,
      );
    } catch (_) {
      return false;
    }
  }
}
