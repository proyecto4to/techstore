import 'dart:async';

import 'package:connectivity_plus/connectivity_plus.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../app/environment.dart';
import '../cache/cache_store.dart';
import '../database/app_database.dart';
import '../logging/app_logger.dart';
import '../native/app_excursion.dart';
import '../native/biometric_service.dart';
import '../native/document_service.dart';
import '../native/haptics_service.dart';
import '../native/image_upload_service.dart';
import '../native/voice_input_service.dart';
import '../network/api_client.dart';
import '../network/connectivity_service.dart';
import '../offline/offline_queue_service.dart';
import '../push/push_service.dart';
import '../storage/secure_store.dart';
import '../update/apk_installer.dart';
import '../update/app_update_service.dart';
import '../update/self_hosted_updater.dart';

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

/// Actualización cuando el APK lo publica el propio servidor. Convive con el
/// camino de Google Play: la política que devuelve el backend decide cuál usar.
final selfHostedUpdaterProvider = Provider<SelfHostedUpdater>(
  (ref) => SelfHostedUpdater(
    ref.watch(apiClientProvider),
    const AndroidApkInstaller(),
    ref.watch(loggerProvider),
  ),
);

final biometricServiceProvider = Provider<BiometricService>(
  (ref) => BiometricService(ref.watch(secureStoreProvider)),
);

final documentServiceProvider = Provider<DocumentService>(
  (ref) => DocumentService(ref.watch(apiClientProvider)),
);

/// Marca las salidas que la propia app provoca, para que no se confundan con
/// que la persona se fue de la aplicacion.
final appExcursionProvider = Provider<AppExcursion>((ref) => AppExcursion());

final imageUploadServiceProvider = Provider<ImageUploadService>(
  (ref) => ImageUploadService(
    ref.watch(apiClientProvider),
    excursion: ref.watch(appExcursionProvider),
  ),
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

final hapticsServiceProvider = Provider<HapticsService>(
  (ref) => HapticsService(ref.watch(secureStoreProvider)),
);

final voiceInputServiceProvider = Provider<VoiceInputService>((ref) {
  final service = VoiceInputService(ref.watch(loggerProvider));
  ref.onDispose(() => unawaited(service.cancel()));
  return service;
});
