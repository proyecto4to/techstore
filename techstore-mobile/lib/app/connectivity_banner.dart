import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../core/di/providers.dart';
import '../core/offline/offline_queue_service.dart';
import '../features/auth/application/session_controller.dart';

class ConnectivityBanner extends ConsumerStatefulWidget {
  const ConnectivityBanner({required this.child, super.key});

  final Widget child;

  @override
  ConsumerState<ConnectivityBanner> createState() => _ConnectivityBannerState();
}

class _ConnectivityBannerState extends ConsumerState<ConnectivityBanner>
    with WidgetsBindingObserver {
  StreamSubscription<bool>? _subscription;
  bool _online = true;
  bool _hayDatosGuardados = false;
  int _probeGeneration = 0;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    final connectivity = ref.read(connectivityProvider);
    unawaited(_refreshReachability());
    _subscription = connectivity.networkAvailable.listen((_) {
      unawaited(_refreshReachability());
    });
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      unawaited(_refreshReachability());
    }
  }

  Future<void> _refreshReachability() async {
    final generation = ++_probeGeneration;
    final online = await ref.read(connectivityProvider).backendReachable();
    // Sin conexión hay que decir si se está mostrando algo guardado o no:
    // en una instalación recién hecha no hay nada, y prometerlo confunde.
    final guardados = online
        ? _hayDatosGuardados
        : await ref.read(cacheStoreProvider).hasAnyData();
    if (!mounted || generation != _probeGeneration) return;
    if (online == _online && guardados == _hayDatosGuardados) return;
    setState(() {
      _online = online;
      _hayDatosGuardados = guardados;
    });
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    unawaited(_subscription?.cancel());
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final email = ref.watch(authSessionProvider).user?.email;
    final token = ref.read(authSessionProvider.notifier).accessToken;
    final pending = email == null
        ? const Stream<int>.empty()
        : ref
              .watch(offlineQueueProvider)
              .pendingCount(offlineScopeForSession(token, email));
    return StreamBuilder<int>(
      stream: pending,
      initialData: 0,
      builder: (context, snapshot) {
        final count = snapshot.data ?? 0;
        final visible = !_online || count > 0;
        return Column(
          children: [
            if (visible) _aviso(context, count) else const SizedBox.shrink(),
            Expanded(
              // El aviso ya ocupa el lugar de la barra de estado: la pantalla
              // de abajo no tiene que volver a dejarlo, o queda una franja
              // negra entre los dos.
              child: MediaQuery.removePadding(
                context: context,
                removeTop: visible,
                child: widget.child,
              ),
            ),
          ],
        );
      },
    );
  }

  Widget _aviso(BuildContext context, int count) {
    final color = _online
        ? Theme.of(context).colorScheme.tertiaryContainer
        : Theme.of(context).colorScheme.errorContainer;
    final message = !_online
        ? (_hayDatosGuardados
              ? 'Sin conexión · se muestran datos guardados'
              : 'Sin conexión con el servidor')
        : '$count operación${count == 1 ? '' : 'es'} pendiente${count == 1 ? '' : 's'} de sincronizar';
    return Material(
      color: color,
      child: SafeArea(
        bottom: false,
        child: SizedBox(
          width: double.infinity,
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
            child: Text(message, textAlign: TextAlign.center),
          ),
        ),
      ),
    );
  }
}
