import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../di/providers.dart';
import 'haptics_service.dart';

class BiometricGate extends ConsumerStatefulWidget {
  const BiometricGate({required this.child, super.key});

  /// Margen para volver de la cámara, del selector de archivos o de compartir
  /// un PDF sin tener que volver a apoyar el dedo. La pantalla igual se tapa
  /// mientras la app está en segundo plano.
  static const resumeGrace = Duration(seconds: 45);

  final Widget child;

  @override
  ConsumerState<BiometricGate> createState() => _BiometricGateState();
}

class _BiometricGateState extends ConsumerState<BiometricGate>
    with WidgetsBindingObserver {
  bool _loading = true;
  bool _enabled = false;
  bool _unlocked = false;
  bool _authenticating = false;
  DateTime? _pausedAt;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    unawaited(_load());
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (_enabled &&
        (state == AppLifecycleState.paused ||
            state == AppLifecycleState.hidden)) {
      _pausedAt ??= DateTime.now();
      setState(() => _unlocked = false);
    }
    if (state == AppLifecycleState.resumed) {
      unawaited(_refreshOnResume());
    }
  }

  Future<void> _refreshOnResume() async {
    final pausedAt = _pausedAt;
    _pausedAt = null;
    final service = ref.read(biometricServiceProvider);
    final enabled = await service.isEnabled() && await service.isAvailable();
    if (!mounted) return;
    final withinGrace =
        pausedAt != null &&
        DateTime.now().difference(pausedAt) < BiometricGate.resumeGrace;
    setState(() {
      _enabled = enabled;
      if (!enabled || withinGrace) _unlocked = true;
    });
    if (enabled && !_unlocked) await _unlock();
  }

  Future<void> _load() async {
    final service = ref.read(biometricServiceProvider);
    final enabled = await service.isEnabled() && await service.isAvailable();
    if (!mounted) return;
    setState(() {
      _enabled = enabled;
      _unlocked = !enabled;
      _loading = false;
    });
    if (enabled) unawaited(_unlock());
  }

  Future<void> _unlock({bool allowDeviceCredential = false}) async {
    if (_authenticating) return;
    setState(() => _authenticating = true);
    final service = ref.read(biometricServiceProvider);
    final accepted = allowDeviceCredential
        ? await service.confirmIdentity(reason: 'Desbloqueá TechStore')
        : await service.authenticate();
    if (!mounted) return;
    setState(() {
      _unlocked = accepted;
      _authenticating = false;
    });
    if (accepted) {
      unawaited(ref.read(hapticsServiceProvider).play(HapticCue.success));
    }
  }

  @override
  Widget build(BuildContext context) {
    if (_loading || (_enabled && !_unlocked)) {
      return ColoredBox(
        color: Theme.of(context).colorScheme.surface,
        child: Center(
          child: Semantics(
            label: 'TechStore bloqueado',
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                const Icon(Icons.fingerprint, size: 64),
                const SizedBox(height: 16),
                Text(
                  _loading ? 'Comprobando seguridad' : 'TechStore bloqueado',
                ),
                if (!_loading) ...[
                  const SizedBox(height: 12),
                  FilledButton.icon(
                    onPressed: _authenticating ? null : _unlock,
                    icon: const Icon(Icons.lock_open_outlined),
                    label: const Text('Desbloquear'),
                  ),
                  TextButton.icon(
                    onPressed: _authenticating
                        ? null
                        : () => _unlock(allowDeviceCredential: true),
                    icon: const Icon(Icons.pin_outlined),
                    label: const Text('Usar PIN del dispositivo'),
                  ),
                ],
              ],
            ),
          ),
        ),
      );
    }
    return widget.child;
  }
}
