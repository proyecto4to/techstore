import 'package:flutter/painting.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:techstore_mobile/core/diseno/paleta.dart';

/// Contraste según WCAG 2.1.
double contraste(Color a, Color b) {
  final (claro, oscuro) = a.computeLuminance() > b.computeLuminance()
      ? (a.computeLuminance(), b.computeLuminance())
      : (b.computeLuminance(), a.computeLuminance());
  return (claro + 0.05) / (oscuro + 0.05);
}

const superficies = {
  'fondo': Paleta.fondo,
  'negroAzulado': Paleta.negroAzulado,
  'superficie': Paleta.superficie,
  'superficieElevada': Paleta.superficieElevada,
};

void main() {
  test('el texto se lee con AA sobre todas las superficies', () {
    for (final MapEntry(key: nombre, value: superficie) in superficies.entries) {
      expect(
        contraste(Paleta.texto, superficie),
        greaterThanOrEqualTo(4.5),
        reason: 'texto sobre $nombre',
      );
      expect(
        contraste(Paleta.textoSecundario, superficie),
        greaterThanOrEqualTo(4.5),
        reason: 'texto secundario sobre $nombre',
      );
    }
  });

  test('el contorno de un control se distingue (3:1) sobre todas las '
      'superficies', () {
    for (final MapEntry(key: nombre, value: superficie) in superficies.entries) {
      expect(
        contraste(Paleta.bordeDeControl, superficie),
        greaterThanOrEqualTo(3),
        reason: 'borde de control sobre $nombre',
      );
    }
  });

  test('el texto blanco se lee sobre el azul de los botones', () {
    expect(
      contraste(Paleta.texto, Paleta.azulElectrico),
      greaterThanOrEqualTo(4.5),
    );
  });

  test('los colores de estado se leen sobre el relleno de las tarjetas', () {
    for (final (nombre, color) in [
      ('éxito', Paleta.exito),
      ('aviso', Paleta.aviso),
      ('error', Paleta.error),
      ('promoción', Paleta.promocion),
    ]) {
      expect(
        contraste(color, Paleta.negroAzulado),
        greaterThanOrEqualTo(4.5),
        reason: nombre,
      );
    }
  });
}
