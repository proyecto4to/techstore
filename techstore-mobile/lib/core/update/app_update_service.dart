import 'dart:convert';

import 'package:in_app_update/in_app_update.dart';
import 'package:package_info_plus/package_info_plus.dart';
import 'package:url_launcher/url_launcher.dart';

import '../cache/cache_store.dart';
import '../errors/app_failure.dart';
import '../logging/app_logger.dart';
import '../network/api_client.dart';

final class UpdatePolicy {
  const UpdatePolicy({
    required this.currentVersionCode,
    required this.minimumVersionCode,
    required this.latestVersionCode,
    required this.updateAvailable,
    required this.mandatory,
    required this.storeUrl,
  });

  factory UpdatePolicy.fromJson(Map<String, Object?> json) => UpdatePolicy(
    currentVersionCode: (json['currentVersionCode'] as num).toInt(),
    minimumVersionCode: (json['minimumVersionCode'] as num).toInt(),
    latestVersionCode: (json['latestVersionCode'] as num).toInt(),
    updateAvailable: json['updateAvailable'] as bool,
    mandatory: json['mandatory'] as bool,
    storeUrl: Uri.parse(json['storeUrl']! as String),
  );

  final int currentVersionCode;
  final int minimumVersionCode;
  final int latestVersionCode;
  final bool updateAvailable;
  final bool mandatory;
  final Uri storeUrl;

  UpdatePolicy forCurrentVersion(int code) => UpdatePolicy(
    currentVersionCode: code,
    minimumVersionCode: minimumVersionCode,
    latestVersionCode: latestVersionCode,
    updateAvailable: code < latestVersionCode,
    mandatory: code < minimumVersionCode,
    storeUrl: storeUrl,
  );
}

enum UpdateFlow { immediate, flexible, store }

UpdateFlow chooseUpdateFlow(UpdatePolicy policy, AppUpdateInfo info) {
  final available =
      info.updateAvailability == UpdateAvailability.updateAvailable ||
      info.updateAvailability ==
          UpdateAvailability.developerTriggeredUpdateInProgress;
  if (available && policy.mandatory && info.immediateUpdateAllowed) {
    return UpdateFlow.immediate;
  }
  if (available && !policy.mandatory && info.flexibleUpdateAllowed) {
    return UpdateFlow.flexible;
  }
  return UpdateFlow.store;
}

abstract interface class PlayUpdateGateway {
  Future<AppUpdateInfo> check();
  Future<AppUpdateResult> immediate();
  Future<AppUpdateResult> flexible();
  Future<void> completeFlexible();
}

final class GooglePlayUpdateGateway implements PlayUpdateGateway {
  const GooglePlayUpdateGateway();

  @override
  Future<AppUpdateInfo> check() => InAppUpdate.checkForUpdate();
  @override
  Future<AppUpdateResult> immediate() => InAppUpdate.performImmediateUpdate();
  @override
  Future<AppUpdateResult> flexible() => InAppUpdate.startFlexibleUpdate();
  @override
  Future<void> completeFlexible() => InAppUpdate.completeFlexibleUpdate();
}

final class AppUpdateService {
  AppUpdateService(
    this._api,
    this._cache,
    this._play,
    this._logger, {
    Future<PackageInfo> Function()? packageInfo,
    Future<bool> Function(Uri)? openStore,
  }) : _packageInfo = packageInfo ?? PackageInfo.fromPlatform,
       _openStore =
           openStore ??
           ((uri) => launchUrl(uri, mode: LaunchMode.externalApplication));

  static const _cacheScope = 'app:update';
  static const _cacheKey = 'android-policy';

  final ApiClient _api;
  final CacheStore _cache;
  final PlayUpdateGateway _play;
  final AppLogger _logger;
  final Future<PackageInfo> Function() _packageInfo;
  final Future<bool> Function(Uri) _openStore;

  Future<UpdatePolicy?> checkPolicy() async {
    try {
      final info = await _packageInfo();
      final code = int.parse(info.buildNumber);
      try {
        final response = await _api.get<Map<String, Object?>>(
          '/api/v1/app/version',
          queryParameters: {'platform': 'android', 'versionCode': code},
        );
        final json = response.data!;
        await _cache.write(
          scope: _cacheScope,
          key: _cacheKey,
          json: jsonEncode(json),
          ttl: const Duration(hours: 6),
        );
        return UpdatePolicy.fromJson(json);
      } on NetworkFailure {
        final cached = await _cache.readStale(
          scope: _cacheScope,
          key: _cacheKey,
        );
        return cached == null
            ? null
            : UpdatePolicy.fromJson(
                Map<String, Object?>.from(jsonDecode(cached) as Map),
              ).forCurrentVersion(code);
      }
    } on Object catch (error, stackTrace) {
      _logger.warning(
        'No se pudo comprobar la versión Mobile',
        error,
        stackTrace,
      );
      return null;
    }
  }

  Future<bool> install(UpdatePolicy policy) async {
    try {
      final info = await _play.check();
      final flow = chooseUpdateFlow(policy, info);
      if (flow == UpdateFlow.immediate) {
        return await _play.immediate() == AppUpdateResult.success;
      }
      if (flow == UpdateFlow.flexible) {
        final result = await _play.flexible();
        if (result == AppUpdateResult.success) {
          await _play.completeFlexible();
          return true;
        }
        return false;
      }
    } on Object catch (error, stackTrace) {
      _logger.warning('Play In-App Update no disponible', error, stackTrace);
    }
    return _openStore(policy.storeUrl);
  }
}
