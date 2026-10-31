import 'dart:async';

import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../core/di/providers.dart';
import '../core/realtime/agregados_de_sincronizacion.dart';
import '../core/realtime/realtime_event.dart';
import '../core/realtime/realtime_service.dart';
import '../features/administration/presentation/resource_page.dart';
import '../features/auth/application/auth_state.dart';
import '../features/auth/application/session_controller.dart';
import '../features/catalog/application/catalog_providers.dart';
import '../features/chat/presentation/chat_page.dart';
import '../features/checkout/application/checkout_providers.dart';
import '../features/invoices/application/invoice_providers.dart';
import '../features/notifications/presentation/notifications_page.dart';
import '../features/payments/application/payments_providers.dart';

final realtimeServiceProvider = Provider<RealtimeService>((ref) {
  final service = RealtimeService(
    ref.watch(apiClientProvider),
    ref.watch(cacheStoreProvider),
    ref.watch(loggerProvider),
  );
  ref.onDispose(() => unawaited(service.dispose()));
  return service;
});

/// El flujo de eventos, suelto del servicio que lo produce.
///
/// Existe para que una pantalla pueda escuchar los eventos sin depender de
/// [RealtimeService], que es una clase final y no se puede sustituir en una
/// prueba. Con este provider en el medio, un caso le da el flujo que quiera.
final eventosRealtimeProvider = Provider<Stream<RealtimeEvent>>(
  (ref) => ref.watch(realtimeServiceProvider).events,
);

final realtimeCoordinatorProvider = Provider<void>((ref) {
  final service = ref.watch(realtimeServiceProvider);
  final subscription = service.events.listen((event) {
    // La ficha administrativa muestra cualquier tabla, así que se refresca con
    // cualquier evento.
    ref.invalidate(resourceProvider);

    // Los códigos llegan del servidor en inglés y en mayúsculas. La
    // correspondencia vive en `agregados_de_sincronizacion.dart` y no en
    // comparaciones de texto acá: antes se preguntaba `contains('producto')`
    // contra `PRODUCT`, no coincidía nunca, y la aplicación tenía toda la
    // cañería de tiempo real andando sin refrescar nada.
    final agregado = event.aggregate;

    if (agregadosDeCatalogo.contains(agregado)) {
      // El catálogo no se invalida: se actualiza solo, producto por producto,
      // porque tiraría abajo las páginas que la persona ya bajó y la dejaría
      // de vuelta arriba de todo. Lo hace [CatalogoPaginado], que escucha el
      // mismo flujo.
      ref.invalidate(productProvider);
      ref.invalidate(favoritesProvider);
    }
    if (agregadosDePedido.contains(agregado)) {
      ref.invalidate(ordersProvider);
      ref.invalidate(orderProvider);
      ref.invalidate(shipmentProvider);
      // La verificación del pago vive en el pedido: cuando la tienda aprueba o
      // rechaza, el cliente lo ve sin tocar nada, y la bandeja de quien cobra
      // se mueve cuando llega un comprobante.
      ref.invalidate(orderReceiptsProvider);
      ref.invalidate(orderHistoryProvider);
      ref.invalidate(pendingPaymentsProvider);
      ref.invalidate(pendingPaymentsCountProvider);
    }
    if (agregado == 'CONFIGURATION') {
      // Si la tienda cambió de cuenta, nadie tiene que transferir a la vieja.
      ref.invalidate(transferDetailsProvider);
    }
    if (agregadosDeFacturacion.contains(agregado)) {
      ref.invalidate(invoicesProvider);
      ref.invalidate(invoiceProvider);
    }
    if (agregadosDeDirecciones.contains(agregado)) {
      ref.invalidate(addressesProvider);
    }
    if (agregadosDeNotificaciones.contains(agregado)) {
      ref.invalidate(notificationsProvider);
      ref.invalidate(unreadNotificationsProvider);
    }
    if (agregadosDeConversaciones.contains(agregado)) {
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
          // Hay dos canales por empresa y el servidor rechaza al que se
          // suscribe al que no le toca. Quien opera la tienda escucha todo;
          // quien compra, solo catálogo, pedidos y envíos.
          operaLaTienda: next.user?.role.canOperateStore ?? false,
        ),
      );
    }
  }, fireImmediately: true);
});
