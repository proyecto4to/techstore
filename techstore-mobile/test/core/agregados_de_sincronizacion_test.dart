import 'package:flutter_test/flutter_test.dart';
import 'package:techstore_mobile/core/realtime/agregados_de_sincronizacion.dart';

/// La tabla que decide qué refrescar cuando llega un evento del servidor.
///
/// El fallo que motivó estos casos no dio error ni apareció en la consola: la
/// aplicación comparaba `aggregate.contains('producto')` contra los códigos que
/// manda el servidor, que están en inglés y en mayúsculas. No coincidía nunca y
/// no se refrescaba nada. Un código mal escrito acá vuelve a producir
/// exactamente eso, en silencio, así que lo que se comprueba es que la tabla
/// esté completa y sin nombres inventados.
void main() {
  final clasificados = <String>{
    ...agregadosDeCatalogo,
    ...agregadosDePedido,
    ...agregadosDeFacturacion,
    ...agregadosDeDirecciones,
    ...agregadosDeCaja,
    ...agregadosDeNotificaciones,
    ...agregadosDeConversaciones,
    ...agregadosSinPantallaPropia,
  };

  test('todo agregado conocido está clasificado', () {
    final sinClasificar = agregadosConocidos.difference(clasificados);
    expect(
      sinClasificar,
      isEmpty,
      reason:
          'Estos agregados llegan del servidor y nadie sabe qué hacer con '
          'ellos, así que el evento se ignora sin avisar: $sinClasificar. Si '
          'de verdad no hay pantalla que refrescar, van en '
          'agregadosSinPantallaPropia.',
    );
  });

  test('ninguna lista nombra un agregado que el servidor no emite', () {
    final inventados = clasificados.difference(agregadosConocidos);
    expect(
      inventados,
      isEmpty,
      reason:
          'Estos nombres no los manda nadie, así que la pantalla que dependa '
          'de ellos nunca se refresca: $inventados. Suele ser una errata o el '
          'nombre en castellano en lugar del código del disparador.',
    );
  });

  test('los códigos vienen en mayúsculas, como los declara el disparador', () {
    for (final agregado in agregadosConocidos) {
      expect(
        agregado,
        matches(RegExp(r'^[A-Z][A-Z_]*[A-Z]$')),
        reason:
            'El disparador de la base encola el tipo en mayúsculas; la '
            'comparación de acá es exacta y cualquier otra forma no coincide.',
      );
    }
  });

  test(
    'lo que no tiene pantalla propia no figura también como refrescable',
    () {
      final refrescables = <String>{
        ...agregadosDeCatalogo,
        ...agregadosDePedido,
        ...agregadosDeFacturacion,
        ...agregadosDeDirecciones,
        ...agregadosDeCaja,
        ...agregadosDeNotificaciones,
        ...agregadosDeConversaciones,
      };
      expect(
        agregadosSinPantallaPropia.intersection(refrescables),
        isEmpty,
        reason:
            'agregadosSinPantallaPropia significa «no hay nada que hacer». Si '
            'además aparece en otra lista, una de las dos miente.',
      );
    },
  );
}
