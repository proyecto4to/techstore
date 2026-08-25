import 'dart:async';

import 'package:connectivity_plus/connectivity_plus.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../app/environment.dart';
import '../cache/cache_store.dart';
import '../database/app_database.dart';
import '../logging/app_logger.dart';
import '../native/biometric_service.dart';
import '../native/document_service.dart';
import '../native/image_upload_service.dart';
import '../network/api_client.dart';
import '../network/connectivity_service.dart';
import '../offline/offline_queue_service.dart';
import '../push/push_service.dart';
import '../storage/secure_store.dart';
import '../update/app_update_service.dart';

final environmentProvider = Provider<AppEnvironment>((ref) {
  throw StateError('AppEnvironment no fue configurado en bootstrap');
});

final loggerProvider = Provider<AppLogger>((ref) => const SafeAppLogger());

final apiClientProvider = Provider<ApiClient>((ref) {
  final environment = ref.watch(environmentProvider);
  return ApiClient(
    baseUrl: environment.apiBaseUrl,
    logger: ref.watch(loggerProvider),
  );
});

final connectivityProvider = Provider<ConnectivityService>((ref) {
  return ConnectivityService(Connectivity(), ref.watch(apiClientProvider).dio);
});

final secureStoreProvider = Provider<SecureStore>(
  (ref) => AndroidSecureStore(),
);

final databaseProvider = Provider<AppDatabase>((ref) {
  final database = AppDatabase();
  ref.onDispose(database.close);
  return database;
});

final cacheStoreProvider = Provider<CacheStore>(
  (ref) => CacheStore(ref.watch(databaseProvider)),
);

final offlineQueueProvider = Provider<OfflineQueueService>(
  (ref) => OfflineQueueService(
    ref.watch(databaseProvider),
    ref.watch(apiClientProvider),
    ref.watch(connectivityProvider),
    ref.watch(loggerProvider),
  ),
);

final appUpdateServiceProvider = Provider<AppUpdateService>(
  (ref) => AppUpdateService(
    ref.watch(apiClientProvider),
    ref.watch(cacheStoreProvider),
    const GooglePlayUpdateGateway(),
    ref.watch(loggerProvider),
  ),
);

final biometricServiceProvider = Provider<BiometricService>(
  (ref) => BiometricService(ref.watch(secureStoreProvider)),
);

final documentServiceProvider = Provider<DocumentService>(
  (ref) => DocumentService(ref.watch(apiClientProvider)),
);

final imageUploadServiceProvider = Provider<ImageUploadService>(
  (ref) => ImageUploadService(ref.watch(apiClientProvider)),
);

final pushServiceProvider = Provider<PushService>((ref) {
  final service = PushService(
    ref.watch(apiClientProvider),
    ref.watch(secureStoreProvider),
    ref.watch(loggerProvider),
  );
  ref.onDispose(() => unawaited(service.dispose()));
  return service;
});
