import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

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
      builder: (context, child) => ConnectivityBanner(
        child: AppUpdateGate(child: BiometricGate(child: child!)),
      ),
    );
  }
}
