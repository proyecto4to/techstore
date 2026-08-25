import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../di/providers.dart';

class BiometricGate extends ConsumerStatefulWidget {
  const BiometricGate({required this.child, super.key});

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
      setState(() => _unlocked = false);
    }
    if (state == AppLifecycleState.resumed) {
      unawaited(_refreshOnResume());
    }
  }

  Future<void> _refreshOnResume() async {
    final service = ref.read(biometricServiceProvider);
    final enabled = await service.isEnabled() && await service.isAvailable();
    if (!mounted) return;
    setState(() {
      _enabled = enabled;
      if (!enabled) _unlocked = true;
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

  Future<void> _unlock() async {
    if (_authenticating) return;
    setState(() => _authenticating = true);
    final accepted = await ref.read(biometricServiceProvider).authenticate();
    if (!mounted) return;
    setState(() {
      _unlocked = accepted;
      _authenticating = false;
    });
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
