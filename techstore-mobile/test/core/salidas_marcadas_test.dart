import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

/// Toda salida que la app provoca tiene que estar marcada como tal.
///
/// La sesión se cierra en cuanto la app pasa a segundo plano, porque el
/// teléfono se comparte en el mostrador. `AppExcursion` existe para distinguir
/// «la persona se fue» de «la app abrió otra cosa como paso de la operación»:
/// la cámara, un PDF, el marcador del teléfono, el mapa del reparto.
///
/// El mecanismo estaba bien pensado y bien documentado —su comentario nombra
/// el visor de PDF y los diálogos de permisos— pero **solo lo usaba uno de los
/// diez lugares que salen de la app**. El repartidor tocaba «navegar», se abría
/// Google Maps, y al volver tenía que iniciar sesión de nuevo en plena ronda.
/// Lo mismo al llamar al cliente, al compartir una factura o al ir a los
/// ajustes de ubicación.
///
/// Ese fallo no se ve en ningún test de pantalla ni en el analizador: la app
/// funciona, solo que expulsa a la persona. Lo único que lo previene es
/// comprobar la regla sobre el código, y por eso este caso lee las fuentes.
///
/// **Lo que comprueba y lo que no.** Comprueba que un archivo que llama a una
/// de estas APIs conozca `durante(`. No comprueba que cada llamada concreta
/// esté envuelta: eso pediría analizar el árbol sintáctico, y el envoltorio a
/// veces vive dos funciones más arriba —`ImageUploadService` marca la
/// operación entera, no cada paso—. Alcanza para lo que pasó: archivos que
/// salen de la app sin haberse enterado de que existe la marca.
void main() {
  /// Las llamadas que mandan la app a segundo plano.
  ///
  /// Si aparece una nueva forma de salir —otro paquete, otro plugin— va acá.
  const salidas = <String>[
    'launchUrl(',
    'OpenFilex.open(',
    'SharePlus.instance.share(',
    'Printing.sharePdf(',
    'Printing.layoutPdf(',
    'Geolocator.openAppSettings(',
    'Geolocator.openLocationSettings(',
  ];

  /// Archivos que nombran una salida sin provocarla.
  ///
  /// `app_excursion.dart` define el mecanismo; `apk_installer.dart` es la capa
  /// que habla con Android y a quien la envuelve es su llamador.
  const exentos = <String>{'app_excursion.dart', 'apk_installer.dart'};

  test('ningún archivo sale de la app sin marcar la salida', () {
    final infractores = <String, List<String>>{};

    for (final archivo in Directory('lib').listSync(recursive: true)) {
      if (archivo is! File || !archivo.path.endsWith('.dart')) continue;
      final nombre = archivo.uri.pathSegments.last;
      if (exentos.contains(nombre)) continue;

      final fuente = archivo.readAsStringSync();
      final encontradas = salidas.where(fuente.contains).toList();
      if (encontradas.isEmpty) continue;
      if (fuente.contains('durante(')) continue;

      infractores[archivo.path.replaceAll(r'\', '/')] = encontradas;
    }

    expect(
      infractores,
      isEmpty,
      reason:
          'Estos archivos sacan a la persona de la app sin marcarlo, así que '
          'al volver se encuentra la sesión cerrada:\n'
          '${infractores.entries.map((e) => '  ${e.key} -> ${e.value.join(", ")}').join('\n')}\n'
          'Envolvé la llamada en excursion.durante(() => ...), como hace '
          'ImageUploadService con la cámara.',
    );
  });

  test('el mecanismo sigue estando donde dice la regla', () {
    // Si alguien renombra `durante` o mueve la clase, el caso de arriba pasaría
    // a aprobar cualquier cosa sin que nadie se entere.
    final fuente = File('lib/core/native/app_excursion.dart').readAsStringSync();
    expect(fuente, contains('Future<T> durante<T>('));
    expect(fuente, contains('bool get enCurso'));
  });
}
