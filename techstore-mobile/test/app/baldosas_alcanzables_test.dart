import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

/// Cada baldosa del menú de trabajo tiene que llevar a una ruta que exista.
///
/// Esto ya pasó dos veces: una pantalla terminada y probada, con su permiso en
/// el backend, a la que no se podía entrar porque faltaba la ruta en el router
/// o el botón que la abre. Ninguna de las dos cosas se ve mirando tests de la
/// pantalla, porque la pantalla está bien; lo que falta es el camino.
///
/// El caso lee las fuentes en vez de montar la app: montarla con sesión deja
/// temporizadores del coordinador de tiempo real andando y el entorno falla
/// con «A Timer is still pending».
void main() {
  test('toda baldosa del menú apunta a una ruta registrada', () {
    final hub = File(
      'lib/features/administration/presentation/business_hub_page.dart',
    ).readAsStringSync();
    final router = File('lib/app/app_router.dart').readAsStringSync();

    final rutasDeBaldosas = RegExp(r"ruta:\s*'([^']+)'")
        .allMatches(hub)
        .map((coincidencia) => coincidencia.group(1)!)
        .toSet();

    expect(
      rutasDeBaldosas,
      isNotEmpty,
      reason: 'si cambió la forma de declarar las baldosas, hay que ajustar '
          'este caso en lugar de borrarlo',
    );

    final rutasDelRouter = RegExp(r"path:\s*'([^']+)'")
        .allMatches(router)
        .map((coincidencia) => coincidencia.group(1)!)
        .toSet();

    for (final ruta in rutasDeBaldosas) {
      // Las rutas con parámetro (`/resources/:key`) se declaran con el
      // parámetro y se navegan ya resueltas: alcanza con que exista el molde.
      final registrada =
          rutasDelRouter.contains(ruta) ||
          rutasDelRouter.any(
            (registrada) =>
                registrada.contains(':') &&
                ruta.startsWith(registrada.split(':').first),
          );
      expect(
        registrada,
        isTrue,
        reason:
            'La baldosa «$ruta» no lleva a ninguna parte: falta su GoRoute en '
            'app_router.dart',
      );
    }
  });
}
