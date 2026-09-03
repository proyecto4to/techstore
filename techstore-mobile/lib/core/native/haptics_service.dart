import 'package:flutter/services.dart';

import '../storage/secure_store.dart';

/// Señales de confirmación física disponibles para los flujos de la app.
enum HapticCue { tap, selection, success, warning, error }

/// Reproduce el feedback háptico y sonoro del dispositivo.
///
/// Es deliberadamente best-effort: un equipo sin motor de vibración, sin canal
/// de plataforma (tests, escritorio) o con la preferencia apagada nunca debe
/// interrumpir la operación que lo invoca.
final class HapticsService {
  HapticsService(this._store, {HapticPlayer? player})
    : _player = player ?? const SystemHapticPlayer();

  static const enabledKey = 'techstore.haptics_enabled';

  final SecureStore _store;
  final HapticPlayer _player;
  bool? _enabled;

  /// Activo salvo que el usuario lo haya apagado explícitamente.
  Future<bool> isEnabled() async =>
      _enabled ??= await _store.read(enabledKey) != 'false';

  Future<void> setEnabled(bool enabled) async {
    _enabled = enabled;
    await _store.write(enabledKey, enabled ? 'true' : 'false');
    if (enabled) await play(HapticCue.selection);
  }

  Future<void> play(HapticCue cue) async {
    if (!await isEnabled()) return;
    try {
      await _player.play(cue);
    } catch (_) {
      // El feedback es accesorio: nunca puede romper el flujo que lo pidió.
    }
  }
}

abstract interface class HapticPlayer {
  Future<void> play(HapticCue cue);
}

final class SystemHapticPlayer implements HapticPlayer {
  const SystemHapticPlayer();

  @override
  Future<void> play(HapticCue cue) async {
    switch (cue) {
      case HapticCue.tap:
        await HapticFeedback.selectionClick();
      case HapticCue.selection:
        await HapticFeedback.lightImpact();
      case HapticCue.success:
        await HapticFeedback.mediumImpact();
        await SystemSound.play(SystemSoundType.click);
      case HapticCue.warning:
        await HapticFeedback.heavyImpact();
      case HapticCue.error:
        await HapticFeedback.vibrate();
        await SystemSound.play(SystemSoundType.alert);
    }
  }
}
