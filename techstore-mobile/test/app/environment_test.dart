import 'package:flutter_test/flutter_test.dart';
import 'package:techstore_mobile/app/environment.dart';

void main() {
  test('development usa el backend del emulador por defecto', () {
    final environment = AppEnvironment.development();
    expect(environment.flavor, AppFlavor.development);
    expect(environment.apiBaseUrl, 'http://10.0.2.2:8090');
  });

  test('staging no acepta una URL vacía', () {
    expect(AppEnvironment.staging, throwsStateError);
  });

  test('private requiere una URL explícita de build', () {
    expect(AppEnvironment.privateDistribution, throwsStateError);
  });
}
