import 'package:flutter_test/flutter_test.dart';
import 'package:in_app_update/in_app_update.dart';
import 'package:techstore_mobile/core/update/app_update_service.dart';

void main() {
  test('interpreta política opcional y obligatoria del backend', () {
    final mandatory = UpdatePolicy.fromJson({
      'currentVersionCode': 2,
      'minimumVersionCode': 3,
      'latestVersionCode': 5,
      'updateAvailable': true,
      'mandatory': true,
      'storeUrl': 'https://play.google.com/store/apps/details?id=test',
    });
    final optional = UpdatePolicy.fromJson({
      'currentVersionCode': 4,
      'minimumVersionCode': 3,
      'latestVersionCode': 5,
      'updateAvailable': true,
      'mandatory': false,
      'storeUrl': 'https://play.google.com/store/apps/details?id=test',
    });

    expect(mandatory.mandatory, isTrue);
    expect(optional.mandatory, isFalse);
    expect(optional.storeUrl.scheme, 'https');
    expect(mandatory.forCurrentVersion(5).updateAvailable, isFalse);
    expect(mandatory.forCurrentVersion(5).mandatory, isFalse);
    expect(
      chooseUpdateFlow(mandatory, _info(immediate: true)),
      UpdateFlow.immediate,
    );
    expect(
      chooseUpdateFlow(optional, _info(flexible: true)),
      UpdateFlow.flexible,
    );
    expect(chooseUpdateFlow(mandatory, _info()), UpdateFlow.store);
  });
}

AppUpdateInfo _info({bool immediate = false, bool flexible = false}) =>
    AppUpdateInfo(
      updateAvailability: UpdateAvailability.updateAvailable,
      immediateUpdateAllowed: immediate,
      immediateAllowedPreconditions: const [],
      flexibleUpdateAllowed: flexible,
      flexibleAllowedPreconditions: const [],
      availableVersionCode: 5,
      installStatus: InstallStatus.unknown,
      packageName: 'py.com.techstore.app',
      clientVersionStalenessDays: 1,
      updatePriority: 3,
    );
