import 'dart:io';

import 'package:drift/native.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:in_app_update/in_app_update.dart';
import 'package:package_info_plus/package_info_plus.dart';
import 'package:techstore_mobile/core/cache/cache_store.dart';
import 'package:techstore_mobile/core/database/app_database.dart';
import 'package:techstore_mobile/core/logging/app_logger.dart';
import 'package:techstore_mobile/core/network/api_client.dart';
import 'package:techstore_mobile/core/update/app_update_service.dart';

void main() {
  final baseUrl = Platform.environment['TECHSTORE_E2E_BASE_URL'];
  final expectedMinimum = int.tryParse(
    Platform.environment['TECHSTORE_E2E_EXPECTED_MIN_VERSION'] ?? '',
  );
  final expectedLatest = int.tryParse(
    Platform.environment['TECHSTORE_E2E_EXPECTED_LATEST_VERSION'] ?? '',
  );
  final expectedMandatory =
      Platform.environment['TECHSTORE_E2E_EXPECTED_MANDATORY'] == 'true';
  final enabled =
      baseUrl != null && expectedMinimum != null && expectedLatest != null;

  test('Mobile v1 interpreta la politica v2 real del backend', () async {
    final database = AppDatabase(NativeDatabase.memory());
    addTearDown(database.close);
    final service = AppUpdateService(
      ApiClient(baseUrl: baseUrl!, logger: const _SilentLogger()),
      CacheStore(database),
      const _UnusedPlayGateway(),
      const _SilentLogger(),
      packageInfo: () async => PackageInfo(
        appName: 'TechStore',
        packageName: 'py.com.techstore.app',
        version: '1.0.0',
        buildNumber: '1',
      ),
    );

    final policy = await service.checkPolicy();

    expect(policy, isNotNull);
    expect(policy!.currentVersionCode, 1);
    expect(policy.minimumVersionCode, expectedMinimum);
    expect(policy.latestVersionCode, expectedLatest);
    expect(policy.updateAvailable, isTrue);
    expect(policy.mandatory, expectedMandatory);
    expect(policy.storeUrl.scheme, 'https');
    // ignore: avoid_print
    print(
      'UPDATE_POLICY_E2E_OK current=1 latest=$expectedLatest '
      'minimum=$expectedMinimum mandatory=$expectedMandatory',
    );
  }, skip: enabled ? false : 'Requiere backend y expectativas E2E.');
}

final class _UnusedPlayGateway implements PlayUpdateGateway {
  const _UnusedPlayGateway();

  @override
  Future<AppUpdateInfo> check() => throw UnsupportedError('No usado');
  @override
  Future<AppUpdateResult> flexible() => throw UnsupportedError('No usado');
  @override
  Future<AppUpdateResult> immediate() => throw UnsupportedError('No usado');
  @override
  Future<void> completeFlexible() => throw UnsupportedError('No usado');
}

final class _SilentLogger implements AppLogger {
  const _SilentLogger();

  @override
  void debug(String message) {}
  @override
  void error(String message, [Object? error, StackTrace? stackTrace]) {}
  @override
  void info(String message) {}
  @override
  void warning(String message, [Object? error, StackTrace? stackTrace]) {}
}
