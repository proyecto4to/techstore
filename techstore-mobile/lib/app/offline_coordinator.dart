import 'dart:async';

import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../core/di/providers.dart';
import '../core/offline/offline_queue_service.dart';
import '../features/auth/application/auth_state.dart';
import '../features/auth/application/session_controller.dart';
import '../features/chat/presentation/chat_page.dart';

final offlineCoordinatorProvider = Provider<void>((ref) {
  final queue = ref.watch(offlineQueueProvider);

  Future<void> processAuthenticated() async {
    final auth = ref.read(authSessionProvider);
    final email = auth.user?.email;
    if (!auth.isAuthenticated || email == null) return;
    final token = ref.read(authSessionProvider.notifier).accessToken;
    await queue.process(offlineScopeForSession(token, email));
    ref.invalidate(conversationsProvider);
    ref.invalidate(messagesProvider);
  }

  final network = ref
      .watch(connectivityProvider)
      .networkAvailable
      .where((available) => available)
      .listen((_) => unawaited(processAuthenticated()));
  ref.onDispose(network.cancel);

  ref.listen<AuthState>(authSessionProvider, (previous, next) {
    if (next.isAuthenticated) unawaited(processAuthenticated());
  }, fireImmediately: true);
});
