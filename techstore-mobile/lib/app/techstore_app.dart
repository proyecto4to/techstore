import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../core/auth/session_guard.dart';
import '../core/native/biometric_gate.dart';
import 'app_router.dart';
import 'app_theme.dart';
import 'app_update_gate.dart';
import 'connectivity_banner.dart';
import 'environment.dart';
import 'offline_coordinator.dart';
import 'push_coordinator.dart';
import 'realtime_coordinator.dart';

class TechStoreApp extends ConsumerWidget {
  const TechStoreApp({required this.environment, super.key});

  final AppEnvironment environment;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    ref.watch(realtimeCoordinatorProvider);
    ref.watch(pushCoordinatorProvider);
    ref.watch(offlineCoordinatorProvider);
    return MaterialApp.router(
      debugShowCheckedModeBanner: environment.flavor != AppFlavor.production,
      title: 'TechStore',
      theme: AppTheme.light(),
      darkTheme: AppTheme.dark(),
      themeMode: ThemeMode.system,
      routerConfig: ref.watch(routerProvider),
      // SessionGuard va por fuera: tiene que enterarse del cambio de ciclo de
      // vida aunque arriba haya un bloqueo biométrico o un aviso de versión.
      builder: (context, child) => SessionGuard(
        child: ConnectivityBanner(
          // El respeto por la barra inferior del sistema se resuelve una vez
          // acá y no pantalla por pantalla: hay más de veinte, y la que se
          // agregue mañana volvería a quedar tapada. Solo `bottom`, porque el
          // margen de arriba ya lo manejan el propio banner y cada AppBar.
          //
          // No duplica el margen donde ya había un SafeArea: este consume el
          // padding del MediaQuery, así que los de adentro no agregan nada. Con
          // el teclado abierto tampoco sobra espacio, porque mientras está
          // arriba el sistema deja de reportar el margen de la barra.
          child: SafeArea(
            top: false,
            child: AppUpdateGate(child: BiometricGate(child: child!)),
          ),
        ),
      ),
    );
  }
}
