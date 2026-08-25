import 'dart:async';

import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../core/di/providers.dart';
import '../core/realtime/realtime_service.dart';
import '../features/administration/presentation/resource_page.dart';
import '../features/auth/application/auth_state.dart';
import '../features/auth/application/session_controller.dart';
import '../features/catalog/application/catalog_providers.dart';
import '../features/chat/presentation/chat_page.dart';
import '../features/checkout/application/checkout_providers.dart';
import '../features/invoices/application/invoice_providers.dart';
import '../features/notifications/presentation/notifications_page.dart';

final realtimeServiceProvider = Provider<RealtimeService>((ref) {
  final service = RealtimeService(
    ref.watch(apiClientProvider),
    ref.watch(cacheStoreProvider),
    ref.watch(loggerProvider),
  );
  ref.onDispose(() => unawaited(service.dispose()));
  return service;
});

final realtimeCoordinatorProvider = Provider<void>((ref) {
  final service = ref.watch(realtimeServiceProvider);
  final subscription = service.events.listen((event) {
    ref.invalidate(resourceProvider);
    final aggregate = event.aggregate.toLowerCase();
    if (aggregate.contains('producto') || aggregate.contains('marca')) {
      ref.invalidate(catalogProvider);
      ref.invalidate(productProvider);
      ref.invalidate(favoritesProvider);
    }
    if (aggregate.contains('pedido') ||
        aggregate.contains('pago') ||
        aggregate.contains('factura') ||
        aggregate.contains('envio')) {
      ref.invalidate(ordersProvider);
      ref.invalidate(orderProvider);
      ref.invalidate(shipmentProvider);
    }
    if (aggregate.contains('factura') || aggregate.contains('pago')) {
      ref.invalidate(invoicesProvider);
      ref.invalidate(invoiceProvider);
    }
    if (aggregate.contains('notificacion')) {
      ref.invalidate(notificationsProvider);
    }
    if (aggregate.contains('conversacion') || aggregate.contains('mensaje')) {
      ref.invalidate(conversationsProvider);
      ref.invalidate(messagesProvider);
    }
  });
  ref.onDispose(subscription.cancel);

  ref.listen<AuthState>(authSessionProvider, (previous, next) {
    if (!next.isAuthenticated) {
      service.disconnect();
      return;
    }
    final token = ref.read(authSessionProvider.notifier).accessToken;
    if (token != null) {
      unawaited(
        service.connect(
          baseUrl: ref.read(environmentProvider).apiBaseUrl,
          accessToken: token,
        ),
      );
    }
  }, fireImmediately: true);
});
