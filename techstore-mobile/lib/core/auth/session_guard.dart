import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../features/auth/application/session_controller.dart';
import '../di/providers.dart';

/// Cierra la sesión en cuanto la app deja de estar en primer plano.
///
/// El teléfono es compartido: cajeros que se turnan en el mostrador, un
/// repartidor que presta el equipo, un cliente que deja el celular sobre la
/// mesa. Con la sesión viva en segundo plano, cualquiera que vuelva a abrir la
/// app entra como la persona anterior. Acá se corta apenas la app se va.
///
/// La excepción es lo que la propia app abrió —cámara, galería, un permiso, un
/// PDF—: eso no es «salir de la aplicación», es un paso de la operación en
/// curso. Ver [AppExcursion].
class SessionGuard extends ConsumerStatefulWidget {
  const SessionGuard({required this.child, super.key});

  final Widget child;

  @override
  ConsumerState<SessionGuard> createState() => _SessionGuardState();
}

class _SessionGuardState extends ConsumerState<SessionGuard>
    with WidgetsBindingObserver {
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    // `paused` es irse de la app; `hidden` la cubre en las versiones de Flutter
    // que lo emiten antes de pausar. `inactive` no alcanza: lo dispara también
    // el centro de notificaciones al deslizarse, sin que nadie salga de nada.
    final seFue =
        state == AppLifecycleState.paused || state == AppLifecycleState.hidden;
    if (!seFue) return;
    if (ref.read(appExcursionProvider).enCurso) return;
    unawaited(ref.read(authSessionProvider.notifier).closeForBackground());
  }

  @override
  Widget build(BuildContext context) => widget.child;
}
