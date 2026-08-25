import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/di/providers.dart';
import '../../orders/data/orders_repository.dart';
import '../../orders/domain/order.dart';
import '../data/checkout_repository.dart';
import '../domain/checkout_models.dart';

final checkoutRepositoryProvider = Provider<CheckoutRepository>(
  (ref) => CheckoutRepository(ref.watch(apiClientProvider)),
);

final ordersRepositoryProvider = Provider<OrdersRepository>(
  (ref) => OrdersRepository(ref.watch(apiClientProvider)),
);

final addressesProvider = FutureProvider.autoDispose<List<DeliveryAddress>>(
  (ref) => ref.watch(checkoutRepositoryProvider).addresses(),
);

final ordersProvider = FutureProvider.autoDispose<List<OrderSummary>>(
  (ref) => ref.watch(ordersRepositoryProvider).list(),
);

final orderProvider = FutureProvider.autoDispose
    .family<Map<String, Object?>, int>(
      (ref, id) => ref.watch(ordersRepositoryProvider).get(id),
    );

final shipmentProvider = FutureProvider.autoDispose
    .family<Map<String, Object?>, int>(
      (ref, id) => ref.watch(ordersRepositoryProvider).shipment(id),
    );
