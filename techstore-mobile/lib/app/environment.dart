enum AppFlavor { development, privateDistribution, staging, production }

final class AppEnvironment {
  AppEnvironment._({required this.flavor, required this.apiBaseUrl}) {
    final uri = Uri.tryParse(apiBaseUrl);
    if (uri == null || !uri.hasScheme || !uri.hasAuthority) {
      throw StateError('API_BASE_URL debe ser una URL absoluta');
    }
    if (flavor != AppFlavor.development &&
        flavor != AppFlavor.privateDistribution &&
        uri.scheme != 'https') {
      throw StateError('Staging y producción requieren HTTPS');
    }
  }

  factory AppEnvironment.development() => AppEnvironment._(
    flavor: AppFlavor.development,
    apiBaseUrl: const String.fromEnvironment(
      'API_BASE_URL',
      defaultValue: 'http://10.0.2.2:8090',
    ),
  );

  factory AppEnvironment.staging() => AppEnvironment._(
    flavor: AppFlavor.staging,
    apiBaseUrl: const String.fromEnvironment('API_BASE_URL'),
  );

  factory AppEnvironment.privateDistribution() => AppEnvironment._(
    flavor: AppFlavor.privateDistribution,
    apiBaseUrl: const String.fromEnvironment('API_BASE_URL'),
  );

  factory AppEnvironment.production() => AppEnvironment._(
    flavor: AppFlavor.production,
    apiBaseUrl: const String.fromEnvironment('API_BASE_URL'),
  );

  final AppFlavor flavor;
  final String apiBaseUrl;

  String get label => switch (flavor) {
    AppFlavor.development => 'Development',
    AppFlavor.privateDistribution => 'Private',
    AppFlavor.staging => 'Staging',
    AppFlavor.production => 'Production',
  };
}
