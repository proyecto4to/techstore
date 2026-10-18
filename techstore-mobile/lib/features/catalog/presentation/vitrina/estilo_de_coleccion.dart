import 'dart:math' as math;

import 'package:flutter/material.dart';

import '../../../../core/diseno/paleta.dart';

/// El dibujo de fondo de una colección. Va casi invisible —entre 4 % y 8 % de
/// opacidad—: le da a cada marca su textura sin competir con el producto.
enum PatronDeFondo {
  /// Barrido horizontal, como una pantalla: lo gamer.
  barrido,

  /// Pistas en ángulo recto: componentes, lo que va adentro del gabinete.
  circuito,

  /// Ondas concéntricas: señal, redes.
  ondas,

  /// Grilla de puntos: almacenamiento, memoria.
  matriz,

  /// Retícula fina: oficina, impresión, energía.
  reticula,

  /// Diagonales al ángulo de la marca (32°): periféricos y audio.
  diagonales,
}

/// Cómo se viste una colección. Las colecciones son las marcas: es la única
/// agrupación que tienen los datos, y el catálogo no inventa rubros.
@immutable
class EstiloDeColeccion {
  const EstiloDeColeccion({required this.patron, required this.acento});

  final PatronDeFondo patron;

  /// Uno de los azules de la marca: tiñe el halo y la marca de agua.
  final Color acento;

  /// Dirección de arte por marca. Agrupa marcas parecidas para que la
  /// vidriera tenga ritmo; una marca nueva cae en [paraMarca] a un estilo
  /// estable calculado de su nombre, así nunca queda sin vestir.
  static const _porMarca = <String, EstiloDeColeccion>{
    'redragon': _gamer,
    'hyperx': _gamer,
    'cougar': _gamer,
    'asus': _gamer,
    'nvidia': _componente,
    'amd': _componente,
    'intel': _componente,
    'corsair': _componente,
    'noctua': _componente,
    'fractal design': _componente,
    'kingston': _almacenamiento,
    'samsung': _almacenamiento,
    'western digital': _almacenamiento,
    'sandisk': _almacenamiento,
    'tp-link': _redes,
    'logitech': _periferico,
    'genius': _periferico,
    'trust': _periferico,
    'jbl': _periferico,
    'hp': _oficina,
    'epson': _oficina,
    'apc': _oficina,
    'lg': _periferico,
  };

  static const _gamer = EstiloDeColeccion(
    patron: PatronDeFondo.barrido,
    acento: Paleta.cian,
  );
  static const _componente = EstiloDeColeccion(
    patron: PatronDeFondo.circuito,
    acento: Paleta.azulPrincipal,
  );
  static const _almacenamiento = EstiloDeColeccion(
    patron: PatronDeFondo.matriz,
    acento: Paleta.azulLuminoso,
  );
  static const _redes = EstiloDeColeccion(
    patron: PatronDeFondo.ondas,
    acento: Paleta.cian,
  );
  static const _periferico = EstiloDeColeccion(
    patron: PatronDeFondo.diagonales,
    acento: Paleta.azulLuminoso,
  );
  static const _oficina = EstiloDeColeccion(
    patron: PatronDeFondo.reticula,
    acento: Paleta.azulPrincipal,
  );

  static const sinMarca = EstiloDeColeccion(
    patron: PatronDeFondo.reticula,
    acento: Paleta.azulPrincipal,
  );

  static EstiloDeColeccion paraMarca(String? marca) {
    final clave = marca?.trim().toLowerCase() ?? '';
    if (clave.isEmpty) return sinMarca;
    final conocido = _porMarca[clave];
    if (conocido != null) return conocido;
    // Suma de los códigos y no `hashCode`: el hash de un String puede cambiar
    // entre ejecuciones, y una marca no puede cambiar de traje al reabrir.
    final semilla = clave.codeUnits.fold<int>(0, (a, b) => a + b);
    const acentos = [Paleta.azulPrincipal, Paleta.azulLuminoso, Paleta.cian];
    return EstiloDeColeccion(
      patron: PatronDeFondo.values[semilla % PatronDeFondo.values.length],
      acento: acentos[semilla % acentos.length],
    );
  }

  @override
  bool operator ==(Object other) =>
      other is EstiloDeColeccion &&
      other.patron == patron &&
      other.acento == acento;

  @override
  int get hashCode => Object.hash(patron, acento);
}

/// Dibuja el patrón de una colección. [desplazamiento] mueve el dibujo en
/// sentido contrario al contenido para dar profundidad (paralaje).
class PintorDePatron extends CustomPainter {
  PintorDePatron({
    required this.estilo,
    this.opacidad = 0.07,
    this.desplazamiento = Offset.zero,
  });

  final EstiloDeColeccion estilo;
  final double opacidad;
  final Offset desplazamiento;

  @override
  void paint(Canvas canvas, Size size) {
    final linea = Paint()
      ..color = estilo.acento.withValues(alpha: opacidad)
      ..strokeWidth = 1
      ..style = PaintingStyle.stroke
      ..isAntiAlias = true;
    canvas
      ..save()
      ..clipRect(Offset.zero & size)
      ..translate(desplazamiento.dx, desplazamiento.dy);
    switch (estilo.patron) {
      case PatronDeFondo.barrido:
        for (var y = -40.0; y < size.height + 40; y += 6) {
          canvas.drawLine(Offset(-40, y), Offset(size.width + 40, y), linea);
        }
      case PatronDeFondo.circuito:
        _circuito(canvas, size, linea);
      case PatronDeFondo.ondas:
        final centro = Offset(size.width * 0.72, size.height * 0.38);
        final radioMaximo = size.longestSide * 1.1;
        for (var r = 18.0; r < radioMaximo; r += 18) {
          canvas.drawCircle(centro, r, linea);
        }
      case PatronDeFondo.matriz:
        final punto = Paint()
          ..color = estilo.acento.withValues(alpha: opacidad * 1.6);
        for (var y = -20.0; y < size.height + 20; y += 14) {
          for (var x = -20.0; x < size.width + 20; x += 14) {
            canvas.drawCircle(Offset(x, y), 1.1, punto);
          }
        }
      case PatronDeFondo.reticula:
        for (var x = -40.0; x < size.width + 40; x += 28) {
          canvas.drawLine(Offset(x, -40), Offset(x, size.height + 40), linea);
        }
        for (var y = -40.0; y < size.height + 40; y += 28) {
          canvas.drawLine(Offset(-40, y), Offset(size.width + 40, y), linea);
        }
      case PatronDeFondo.diagonales:
        // El ángulo del corte del isotipo: sube 0,625 por cada unidad.
        final alto = size.width * 0.625;
        for (var x = -size.height * 1.6; x < size.width + 40; x += 16) {
          canvas.drawLine(
            Offset(x, size.height + 20),
            Offset(x + size.width, size.height + 20 - alto),
            linea,
          );
        }
    }
    canvas.restore();
  }

  /// Pistas que avanzan en ángulo recto, siempre las mismas para el mismo
  /// tamaño: una semilla fija, para que no titilen al redibujar.
  void _circuito(Canvas canvas, Size size, Paint linea) {
    final azar = math.Random(31);
    final nodo = Paint()..color = linea.color;
    for (var i = 0; i < 14; i++) {
      var punto = Offset(
        azar.nextDouble() * size.width,
        azar.nextDouble() * size.height,
      );
      final camino = Path()..moveTo(punto.dx, punto.dy);
      for (var tramo = 0; tramo < 3; tramo++) {
        final largo = 24 + azar.nextDouble() * 70;
        punto = tramo.isEven
            ? punto.translate(azar.nextBool() ? largo : -largo, 0)
            : punto.translate(0, azar.nextBool() ? largo : -largo);
        camino.lineTo(punto.dx, punto.dy);
      }
      canvas
        ..drawPath(camino, linea)
        ..drawCircle(punto, 2.2, nodo);
    }
  }

  @override
  bool shouldRepaint(PintorDePatron viejo) =>
      viejo.estilo != estilo ||
      viejo.opacidad != opacidad ||
      viejo.desplazamiento != desplazamiento;
}
