import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../core/di/providers.dart';
import '../core/update/app_update_service.dart';

class AppUpdateGate extends ConsumerStatefulWidget {
  const AppUpdateGate({required this.child, super.key});

  final Widget child;

  @override
  ConsumerState<AppUpdateGate> createState() => _AppUpdateGateState();
}

class _AppUpdateGateState extends ConsumerState<AppUpdateGate> {
  UpdatePolicy? _mandatory;
  bool _installing = false;
  bool _optionalShown = false;

  @override
  void initState() {
    super.initState();
    unawaited(_check());
  }

  Future<void> _check() async {
    final policy = await ref.read(appUpdateServiceProvider).checkPolicy();
    if (!mounted || policy == null || !policy.updateAvailable) return;
    if (policy.mandatory) {
      setState(() => _mandatory = policy);
      return;
    }
    if (_optionalShown) return;
    _optionalShown = true;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) unawaited(_showOptional(policy));
    });
  }

  Future<void> _showOptional(UpdatePolicy policy) async {
    final accepted = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Actualización disponible'),
        content: const Text(
          'Hay una versión nueva de TechStore. Podés instalarla ahora desde Google Play o continuar.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Más tarde'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('Actualizar'),
          ),
        ],
      ),
    );
    if (accepted == true) {
      await ref.read(appUpdateServiceProvider).install(policy);
    }
  }

  @override
  Widget build(BuildContext context) {
    final policy = _mandatory;
    if (policy == null) return widget.child;
    return Material(
      color: Theme.of(context).colorScheme.surface,
      child: SafeArea(
        child: Center(
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 480),
            child: Padding(
              padding: const EdgeInsets.all(24),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  const Icon(Icons.system_update, size: 64),
                  const SizedBox(height: 20),
                  Text(
                    'Actualización requerida',
                    style: Theme.of(context).textTheme.headlineSmall,
                  ),
                  const SizedBox(height: 12),
                  const Text(
                    'Esta versión ya no es compatible con el servicio. Google Play verificará e instalará la actualización oficial.',
                    textAlign: TextAlign.center,
                  ),
                  const SizedBox(height: 24),
                  FilledButton.icon(
                    onPressed: _installing ? null : () => _install(policy),
                    icon: _installing
                        ? const SizedBox.square(
                            dimension: 18,
                            child: CircularProgressIndicator(strokeWidth: 2),
                          )
                        : const Icon(Icons.shop_outlined),
                    label: const Text('Actualizar con Google Play'),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }

  Future<void> _install(UpdatePolicy policy) async {
    setState(() => _installing = true);
    await ref.read(appUpdateServiceProvider).install(policy);
    if (mounted) setState(() => _installing = false);
  }
}
