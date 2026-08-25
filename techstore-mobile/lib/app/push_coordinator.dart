import 'dart:async';

import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../core/di/providers.dart';
import '../features/auth/application/auth_state.dart';
import '../features/auth/application/session_controller.dart';
import '../features/notifications/presentation/notifications_page.dart';
import 'app_router.dart';

final pushCoordinatorProvider = Provider<void>((ref) {
  final service = ref.watch(pushServiceProvider);
  final links = service.deepLinks.listen((link) {
    final route = pushRoute(link);
    if (route != null) ref.read(routerProvider).go(route);
  });
  final messages = service.foregroundMessages.listen((_) {
    ref.invalidate(notificationsProvider);
  });
  ref.onDispose(() {
    unawaited(links.cancel());
    unawaited(messages.cancel());
  });

  ref.listen<AuthState>(authSessionProvider, (previous, next) {
    if (next.isAuthenticated) {
      unawaited(service.start());
    } else {
      unawaited(service.stop());
    }
  }, fireImmediately: true);
});

String? pushRoute(String value) {
  final uri = Uri.tryParse(value);
  if (uri == null || uri.scheme != 'techstore') return null;
  final id = uri.pathSegments.firstOrNull;
  return switch (uri.host) {
    'pedido' when id != null => '/orders/$id',
    'factura' => id == null ? '/invoices' : '/invoices/$id',
    'notificaciones' => '/notifications',
    _ => null,
  };
}
