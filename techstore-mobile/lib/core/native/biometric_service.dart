import 'package:local_auth/local_auth.dart';

import '../storage/secure_store.dart';

final class BiometricService {
  BiometricService(this._store, {LocalAuthentication? authentication})
    : _authentication = authentication ?? LocalAuthentication();

  static const _enabledKey = 'techstore.biometric_enabled';

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

  Future<bool> isEnabled() async => await _store.read(_enabledKey) == 'true';

  Future<void> setEnabled(bool enabled) async {
    if (enabled) {
      if (!await authenticate(reason: 'Activá el acceso biométrico')) {
        throw StateError('No se pudo validar la biometría.');
      }
      await _store.write(_enabledKey, 'true');
    } else {
      await _store.delete(_enabledKey);
    }
  }

  Future<bool> authenticate({
    String reason = 'Desbloqueá TechStore para continuar',
  }) async {
    try {
      return await _authentication.authenticate(
        localizedReason: reason,
        biometricOnly: true,
        persistAcrossBackgrounding: true,
      );
    } catch (_) {
      return false;
    }
  }
}
