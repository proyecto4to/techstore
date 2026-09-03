import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/di/providers.dart';
import '../data/courier_repository.dart';

final courierRepositoryProvider = Provider<CourierRepository>(
  (ref) => CourierRepository(ref.watch(apiClientProvider)),
);

final courierDashboardProvider =
    FutureProvider.autoDispose<Map<String, Object?>>(
      (ref) => ref.watch(courierRepositoryProvider).dashboard(),
    );

final courierDeliveriesProvider =
    FutureProvider.autoDispose<List<Map<String, Object?>>>(
      (ref) => ref.watch(courierRepositoryProvider).deliveries(),
    );

final courierClientsProvider =
    FutureProvider.autoDispose<List<Map<String, Object?>>>(
      (ref) => ref.watch(courierRepositoryProvider).deliveriesByClient(),
    );

final courierRouteProvider = FutureProvider.autoDispose<Map<String, Object?>>(
  (ref) => ref.watch(courierRepositoryProvider).route(),
);

final courierCashMovementsProvider =
    FutureProvider.autoDispose<List<Map<String, Object?>>>(
      (ref) => ref.watch(courierRepositoryProvider).cashMovements(),
    );

final courierTransfersProvider =
    FutureProvider.autoDispose<List<Map<String, Object?>>>(
      (ref) => ref.watch(courierRepositoryProvider).transfers(),
    );

final courierCashiersProvider =
    FutureProvider.autoDispose<List<Map<String, Object?>>>(
      (ref) => ref.watch(courierRepositoryProvider).cashiers(),
    );

final courierVehiclesProvider =
    FutureProvider.autoDispose<List<Map<String, Object?>>>(
      (ref) => ref.watch(courierRepositoryProvider).vehicles(),
    );

final courierCurrentVehicleProvider =
    FutureProvider.autoDispose<Map<String, Object?>?>(
      (ref) => ref.watch(courierRepositoryProvider).currentVehicleUse(),
    );

void invalidateCourier(WidgetRef ref) {
  ref.invalidate(courierDashboardProvider);
  ref.invalidate(courierDeliveriesProvider);
  ref.invalidate(courierClientsProvider);
  ref.invalidate(courierRouteProvider);
  ref.invalidate(courierCashMovementsProvider);
  ref.invalidate(courierTransfersProvider);
  ref.invalidate(courierVehiclesProvider);
  ref.invalidate(courierCurrentVehicleProvider);
}
