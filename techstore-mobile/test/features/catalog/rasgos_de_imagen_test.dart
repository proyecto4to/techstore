import 'dart:math' as math;
import 'dart:typed_data';

import 'package:flutter/painting.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:techstore_mobile/features/catalog/presentation/vitrina/rasgos_de_imagen.dart';

/// Una imagen chica en RGBA: el borde de un color y el centro de otro.
Uint8List imagen({
  required int ancho,
  required int alto,
  required List<int> Function(int x, int y) borde,
  List<int> centro = const [37, 99, 235, 255],
}) {
  final datos = Uint8List(ancho * alto * 4);
  for (var y = 0; y < alto; y++) {
    for (var x = 0; x < ancho; x++) {
      final esBorde = x < 6 || y < 6 || x >= ancho - 6 || y >= alto - 6;
      final color = esBorde ? borde(x, y) : centro;
      datos.setRange((y * ancho + x) * 4, (y * ancho + x) * 4 + 4, color);
    }
  }
  return datos;
}

void main() {
  test('un fondo blanco parejo es una foto de estudio clara', () {
    final rasgos = AnalizadorDeImagen.analizar(
      imagen(ancho: 48, alto: 36, borde: (_, _) => [250, 250, 250, 255]),
      48,
      36,
    );
    expect(rasgos.tipo, TipoDeFoto.estudio);
    expect(rasgos.fondoClaro, isTrue);
    expect(rasgos.fondo, const Color(0xFFFAFAFA));
    expect(rasgos.apaisada, isTrue);
  });

  test('una sombra suave sobre el blanco sigue siendo estudio', () {
    // La foto del UPS o del monitor: blanco con un degradé leve hacia abajo.
    final rasgos = AnalizadorDeImagen.analizar(
      imagen(
        ancho: 48,
        alto: 48,
        borde: (_, y) {
          final v = 255 - y ~/ 3;
          return [v, v, v, 255];
        },
      ),
      48,
      48,
    );
    expect(rasgos.tipo, TipoDeFoto.estudio);
  });

  test('un borde transparente es un producto recortado', () {
    final rasgos = AnalizadorDeImagen.analizar(
      imagen(ancho: 40, alto: 60, borde: (_, _) => [0, 0, 0, 0]),
      40,
      60,
    );
    expect(rasgos.tipo, TipoDeFoto.recortada);
    expect(rasgos.vertical, isTrue);
    // El tono sale del centro, que es el producto, no del borde vacío.
    expect(rasgos.tono, const Color.fromARGB(255, 37, 99, 235));
  });

  test('un borde lleno de colores distintos es una escena', () {
    final azar = math.Random(7);
    final rasgos = AnalizadorDeImagen.analizar(
      imagen(
        ancho: 48,
        alto: 36,
        borde: (_, _) => [
          azar.nextInt(256),
          azar.nextInt(256),
          azar.nextInt(256),
          255,
        ],
      ),
      48,
      36,
    );
    expect(rasgos.tipo, TipoDeFoto.escena);
    expect(rasgos.fondo, isNull);
  });

  test('un fondo negro parejo es estudio oscuro', () {
    final rasgos = AnalizadorDeImagen.analizar(
      imagen(ancho: 32, alto: 32, borde: (_, _) => [6, 6, 8, 255]),
      32,
      32,
    );
    expect(rasgos.tipo, TipoDeFoto.estudio);
    expect(rasgos.fondoOscuro, isTrue);
    expect(rasgos.fondoClaro, isFalse);
  });

  test('datos incompletos no rompen: la foto cuenta como ausente', () {
    expect(
      AnalizadorDeImagen.analizar(Uint8List(10), 48, 48).tipo,
      TipoDeFoto.ausente,
    );
    expect(
      AnalizadorDeImagen.analizar(Uint8List(0), 0, 0).tipo,
      TipoDeFoto.ausente,
    );
  });
}
