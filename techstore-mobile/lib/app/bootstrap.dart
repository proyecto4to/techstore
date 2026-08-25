import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/date_symbol_data_local.dart';

import '../core/di/providers.dart';
import '../core/push/firebase_configuration.dart';
import 'environment.dart';
import 'techstore_app.dart';

Future<void> bootstrap(AppEnvironment environment) async {
  WidgetsFlutterBinding.ensureInitialized();
  await initializeDateFormatting('es_PY');
  await FirebaseConfiguration.initialize();
  runApp(
    ProviderScope(
      overrides: [environmentProvider.overrideWithValue(environment)],
      child: TechStoreApp(environment: environment),
    ),
  );
}
