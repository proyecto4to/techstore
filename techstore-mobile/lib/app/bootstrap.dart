import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/date_symbol_data_local.dart';

import '../core/di/providers.dart';
import '../core/push/firebase_configuration.dart';
import 'environment.dart';
import 'techstore_app.dart';

Future<void> bootstrap(AppEnvironment environment) async {
  WidgetsFlutterBinding.ensureInitialized();
  _registrarLicenciasDeFuentes();
  await initializeDateFormatting('es_PY');
  await FirebaseConfiguration.initialize();
  runApp(
    ProviderScope(
      overrides: [environmentProvider.overrideWithValue(environment)],
      child: TechStoreApp(environment: environment),
    ),
  );
}

/// Las tres tipografías de la marca tienen licencia SIL OFL, que pide viajar
/// con la fuente. Se suman a la lista de licencias que muestra Flutter; se
/// leen recién cuando alguien abre esa pantalla, no al arrancar.
void _registrarLicenciasDeFuentes() {
  LicenseRegistry.addLicense(() async* {
    for (final (familia, archivo) in const [
      ('Inter', 'OFL-Inter.txt'),
      ('Space Grotesk', 'OFL-SpaceGrotesk.txt'),
      ('JetBrains Mono', 'OFL-JetBrainsMono.txt'),
    ]) {
      yield LicenseEntryWithLineBreaks(
        [familia],
        await rootBundle.loadString('assets/fonts/$archivo'),
      );
    }
  });
}
