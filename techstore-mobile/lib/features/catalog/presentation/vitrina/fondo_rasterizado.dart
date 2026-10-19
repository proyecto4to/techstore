import 'dart:math' as math;
import 'dart:ui' as ui;

import 'package:flutter/rendering.dart';

/// Dibuja una sola vez un fondo decorativo y lo reutiliza como imagen.
///
/// El fondo de cada escenario —la luz, el patrón de la colección, la marca de
/// agua en contorno— son cientos de líneas y un texto enorme trazado como
/// figura. Impeller, el motor de dibujo de Flutter, no guarda dibujos en
/// caché: sin esto, cada tarjeta lo volvía a teselar en cada cuadro del
/// desplazamiento, y en el emulador un tercio de los cuadros llegaba tarde.
/// Una imagen se dibuja casi gratis.
///
/// Se rasteriza a 1,5 veces la resolución lógica como mucho: es contenido
/// suave y casi transparente, la diferencia con la nativa no se ve y ocupa la
/// mitad de memoria.
abstract final class FondosRasterizados {
  static const _maximo = 48;
  static const escalaMaxima = 1.5;

  // Un mapa literal de Dart conserva el orden de inserción: el primero es el
  // menos usado.
  static final _imagenes = <String, ui.Image>{};

  static ui.Image obtener({
    required String clave,
    required Size tamano,
    required double escala,
    required void Function(Canvas canvas, Size tamano) dibujar,
  }) {
    final e = math.min(escala, escalaMaxima);
    final ancho = math.max(1, (tamano.width * e).ceil());
    final alto = math.max(1, (tamano.height * e).ceil());
    final llave = '$clave|$ancho×$alto';
    final existente = _imagenes.remove(llave);
    if (existente != null) {
      // Se reinserta al final: es la más recientemente usada.
      _imagenes[llave] = existente;
      return existente;
    }
    final grabadora = ui.PictureRecorder();
    final lienzo = Canvas(grabadora)..scale(ancho / tamano.width, alto / tamano.height);
    dibujar(lienzo, tamano);
    final dibujo = grabadora.endRecording();
    final imagen = dibujo.toImageSync(ancho, alto);
    dibujo.dispose();
    _imagenes[llave] = imagen;
    while (_imagenes.length > _maximo) {
      // Liberar la más vieja no rompe un cuadro en curso: el dibujo que la
      // usa guarda su propia referencia.
      _imagenes.remove(_imagenes.keys.first)?.dispose();
    }
    return imagen;
  }

  /// Para las pruebas.
  static int get cantidad => _imagenes.length;
}

/// Pinta un fondo rasterizado, corrido [desplazamiento] puntos para el
/// paralaje. El fondo se dibuja con [margen] de sobra a cada lado, así correrlo
/// no deja ver el borde.
class PintorDeFondoRasterizado extends CustomPainter {
  PintorDeFondoRasterizado({
    required this.clave,
    required this.escala,
    required this.dibujar,
    this.desplazamiento = Offset.zero,
    this.margen = 0,
  });

  final String clave;
  final double escala;
  final void Function(Canvas canvas, Size tamano) dibujar;
  final Offset desplazamiento;
  final double margen;

  @override
  void paint(Canvas canvas, Size size) {
    if (size.isEmpty) return;
    final total = Size(size.width + margen * 2, size.height + margen * 2);
    final imagen = FondosRasterizados.obtener(
      clave: clave,
      tamano: total,
      escala: escala,
      dibujar: dibujar,
    );
    final destino = Offset(-margen, -margen) + desplazamiento & total;
    canvas
      ..save()
      ..clipRect(Offset.zero & size)
      ..drawImageRect(
        imagen,
        Rect.fromLTWH(0, 0, imagen.width.toDouble(), imagen.height.toDouble()),
        destino,
        Paint()..filterQuality = FilterQuality.low,
      )
      ..restore();
  }

  @override
  bool shouldRepaint(PintorDeFondoRasterizado viejo) =>
      viejo.clave != clave ||
      viejo.desplazamiento != desplazamiento ||
      viejo.escala != escala ||
      viejo.margen != margen;
}
