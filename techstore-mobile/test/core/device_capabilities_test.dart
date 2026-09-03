import 'package:flutter_test/flutter_test.dart';
import 'package:local_auth_platform_interface/local_auth_platform_interface.dart';
import 'package:techstore_mobile/core/native/biometric_service.dart';
import 'package:techstore_mobile/core/native/haptics_service.dart';
import 'package:techstore_mobile/core/storage/secure_store.dart';

void main() {
  group('HapticsService', () {
    test('vibra por omisión y deja de hacerlo al apagar la preferencia', () async {
      final player = _RecordingPlayer();
      final store = _MemorySecureStore();
      final haptics = HapticsService(store, player: player);

      await haptics.play(HapticCue.success);
      expect(player.cues, [HapticCue.success]);

      await haptics.setEnabled(false);
      player.cues.clear();
      await haptics.play(HapticCue.error);
      expect(player.cues, isEmpty);
    });

    test('un motor háptico que falla no propaga la excepción', () async {
      final haptics = HapticsService(
        _MemorySecureStore(),
        player: _BrokenPlayer(),
      );

      await expectLater(haptics.play(HapticCue.tap), completes);
    });
  });

  group('BiometricService.confirmOperation', () {
    test('deja pasar la operación mientras la guarda está apagada', () async {
      final auth = _FakeLocalAuth(accepts: false);
      LocalAuthPlatform.instance = auth;

      addTearDown(() => LocalAuthPlatform.instance = _FakeLocalAuth());
      final service = BiometricService(_MemorySecureStore());
      expect(await service.isOperationGuardEnabled(), isFalse);
      expect(await service.confirmOperation(reason: 'Cerrar caja'), isTrue);
      expect(auth.prompts, 0);
    });

    test('con la guarda activa exige validar y respeta el rechazo', () async {
      final store = _MemorySecureStore()
        ..values['techstore.biometric_guard_operations'] = 'true';
      final auth = _FakeLocalAuth(supported: true, accepts: false);
      LocalAuthPlatform.instance = auth;
      addTearDown(() => LocalAuthPlatform.instance = _FakeLocalAuth());
      final service = BiometricService(store);

      expect(await service.confirmOperation(reason: 'Cerrar caja'), isFalse);
      expect(auth.prompts, 1);
      expect(auth.lastBiometricOnly, isFalse);
    });

    test('no bloquea en equipos sin sensor aunque la guarda esté activa', () async {
      final store = _MemorySecureStore()
        ..values['techstore.biometric_guard_operations'] = 'true';
      final auth = _FakeLocalAuth(supported: false, accepts: false);
      LocalAuthPlatform.instance = auth;
      addTearDown(() => LocalAuthPlatform.instance = _FakeLocalAuth());
      final service = BiometricService(store);

      expect(await service.confirmOperation(reason: 'Cerrar caja'), isTrue);
      expect(auth.prompts, 0);
    });
  });
}

final class _RecordingPlayer implements HapticPlayer {
  final cues = <HapticCue>[];

  @override
  Future<void> play(HapticCue cue) async => cues.add(cue);
}

final class _BrokenPlayer implements HapticPlayer {
  @override
  Future<void> play(HapticCue cue) async =>
      throw StateError('sin motor háptico');
}

final class _FakeLocalAuth extends LocalAuthPlatform {
  _FakeLocalAuth({this.supported = true, this.accepts = true});

  final bool supported;
  final bool accepts;
  int prompts = 0;
  bool? lastBiometricOnly;

  @override
  Future<bool> authenticate({
    required String localizedReason,
    required Iterable<AuthMessages> authMessages,
    AuthenticationOptions options = const AuthenticationOptions(),
  }) async {
    prompts++;
    lastBiometricOnly = options.biometricOnly;
    return accepts;
  }

  @override
  Future<bool> deviceSupportsBiometrics() async => supported;

  @override
  Future<List<BiometricType>> getEnrolledBiometrics() async =>
      supported ? const [BiometricType.fingerprint] : const [];

  @override
  Future<bool> isDeviceSupported() async => supported;

  @override
  Future<bool> stopAuthentication() async => true;
}

final class _MemorySecureStore implements SecureStore {
  final values = <String, String>{};

  @override
  Future<void> clear() async => values.clear();

  @override
  Future<void> delete(String key) async => values.remove(key);

  @override
  Future<String?> read(String key) async => values[key];

  @override
  Future<void> write(String key, String value) async => values[key] = value;
}
