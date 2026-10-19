import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/scheduler.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/date_symbol_data_local.dart';

import '../core/di/providers.dart';
import '../core/push/firebase_configuration.dart';
import 'environment.dart';
import 'techstore_app.dart';

/// Solo para medir rendimiento en un dispositivo:
/// `--dart-define=MEDIR_CUADROS=true`. Apagado, el compilador ni lo incluye.
const _medirCuadros = bool.fromEnvironment('MEDIR_CUADROS');

Future<void> bootstrap(AppEnvironment environment) async {
  WidgetsFlutterBinding.ensureInitialized();
  if (_medirCuadros) _registrarTiemposDeCuadro();
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

/// Cada 90 cuadros escribe en el registro cuánto tardaron en armarse (hilo de
/// la interfaz) y en dibujarse (hilo de rasterizado): dice cuál de los dos
/// hace llegar tarde un cuadro.
void _registrarTiemposDeCuadro() {
  final armado = <double>[];
  final dibujo = <double>[];
  double percentil(List<double> v, double q) =>
      (v.toList()..sort())[((v.length - 1) * q).round()];
  SchedulerBinding.instance.addTimingsCallback((tiempos) {
    for (final t in tiempos) {
      armado.add(t.buildDuration.inMicroseconds / 1000);
      dibujo.add(t.rasterDuration.inMicroseconds / 1000);
    }
    if (armado.length < 90) return;
    // ignore: avoid_print
    print(
      'CUADROS armado med ${percentil(armado, .5).toStringAsFixed(1)} '
      'p90 ${percentil(armado, .9).toStringAsFixed(1)} | dibujo med '
      '${percentil(dibujo, .5).toStringAsFixed(1)} p90 '
      '${percentil(dibujo, .9).toStringAsFixed(1)} ms',
    );
    armado.clear();
    dibujo.clear();
  });
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
