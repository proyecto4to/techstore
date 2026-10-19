import 'dart:math' as math;

import 'package:flutter/foundation.dart';
import 'package:flutter/painting.dart';

/// Qué clase de foto es, que es lo que decide cómo se la presenta.
enum TipoDeFoto {
  /// PNG con transparencia: el producto ya está recortado y puede flotar.
  recortada,

  /// Foto de estudio sobre un fondo parejo (blanco, gris, negro).
  estudio,

  /// Foto con ambiente: el producto en su lugar, con fondo variado.
  escena,

  /// No hay foto, o no se pudo bajar.
  ausente,
}

/// Lo que se sabe de una foto después de mirarla.
///
/// El catálogo real tiene fotos de calidad muy dispar —recortes, fotos de
/// estudio con fondo blanco, escenas con luces de colores— y no hay
/// categorías en los datos. Por eso la vidriera decide la presentación por lo
/// que la foto *es* y no por un rubro que habría que inventar.
@immutable
class RasgosDeImagen {
  const RasgosDeImagen({
    required this.tipo,
    required this.proporcion,
    required this.tono,
    this.fondo,
  });

  factory RasgosDeImagen.fromJson(Map<String, Object?> json) => RasgosDeImagen(
    tipo: TipoDeFoto.values.byName(json['tipo']! as String),
    proporcion: (json['proporcion']! as num).toDouble(),
    tono: Color((json['tono']! as num).toInt()),
    fondo: json['fondo'] == null ? null : Color((json['fondo']! as num).toInt()),
  );

  static const ausente = RasgosDeImagen(
    tipo: TipoDeFoto.ausente,
    proporcion: 1,
    tono: Color(0xFF3B82F6),
  );

  final TipoDeFoto tipo;

  /// Ancho sobre alto.
  final double proporcion;

  /// Color promedio del centro, donde suele estar el producto. Tiñe la luz
  /// de la vitrina, dentro de los azules de la marca.
  final Color tono;

  /// El color del fondo, solo en las fotos de estudio.
  final Color? fondo;

  /// Para guardarlo en el caché local: cada foto se analiza una vez en la vida
  /// de la instalación, no en cada apertura.
  Map<String, Object?> toJson() => {
    'tipo': tipo.name,
    'proporcion': proporcion,
    'tono': tono.toARGB32(),
    'fondo': fondo?.toARGB32(),
  };

  bool get apaisada => proporcion >= 1.3;
  bool get vertical => proporcion <= 0.8;

  bool get fondoClaro =>
      tipo == TipoDeFoto.estudio && fondo!.computeLuminance() > 0.45;

  bool get fondoOscuro =>
      tipo == TipoDeFoto.estudio && fondo!.computeLuminance() < 0.06;

  @override
  bool operator ==(Object other) =>
      other is RasgosDeImagen &&
      other.tipo == tipo &&
      other.proporcion == proporcion &&
      other.tono == tono &&
      other.fondo == fondo;

  @override
  int get hashCode => Object.hash(tipo, proporcion, tono, fondo);

  @override
  String toString() =>
      'RasgosDeImagen($tipo, ${proporcion.toStringAsFixed(2)}, fondo: $fondo)';
}

/// Lee una foto ya reducida (unos 48 píxeles de ancho, en RGBA) y devuelve sus
/// rasgos. Es una función pura: no baja nada ni decodifica, así se prueba con
/// píxeles armados a mano.
abstract final class AnalizadorDeImagen {
  /// Cuántos píxeles del borde se miran de cada lado.
  static const _anilloBorde = 2;

  /// Desvío máximo del borde, en niveles de 0 a 255, para llamarlo «parejo».
  /// Una foto de estudio con su sombra suave queda por debajo; una escena,
  /// muy por encima.
  static const _desvioDeEstudio = 16.0;

  static RasgosDeImagen analizar(Uint8List rgba, int ancho, int alto) {
    if (ancho <= 0 || alto <= 0 || rgba.length < ancho * alto * 4) {
      return RasgosDeImagen.ausente;
    }
    final proporcion = ancho / alto;

    var transparentes = 0;
    var cuenta = 0;
    var sumaR = 0.0, sumaG = 0.0, sumaB = 0.0;
    var sumaR2 = 0.0, sumaG2 = 0.0, sumaB2 = 0.0;

    for (var y = 0; y < alto; y++) {
      for (var x = 0; x < ancho; x++) {
        final enBorde =
            x < _anilloBorde ||
            y < _anilloBorde ||
            x >= ancho - _anilloBorde ||
            y >= alto - _anilloBorde;
        if (!enBorde) continue;
        final i = (y * ancho + x) * 4;
        final a = rgba[i + 3];
        if (a < 24) {
          transparentes++;
          continue;
        }
        final r = rgba[i].toDouble();
        final g = rgba[i + 1].toDouble();
        final b = rgba[i + 2].toDouble();
        sumaR += r;
        sumaG += g;
        sumaB += b;
        sumaR2 += r * r;
        sumaG2 += g * g;
        sumaB2 += b * b;
        cuenta++;
      }
    }

    final tono = _tonoDelCentro(rgba, ancho, alto);
    final total = transparentes + cuenta;
    if (total == 0) return RasgosDeImagen.ausente;

    // Más de la mitad del borde transparente: el producto viene recortado.
    if (transparentes / total > 0.5) {
      return RasgosDeImagen(
        tipo: TipoDeFoto.recortada,
        proporcion: proporcion,
        tono: tono,
      );
    }

    final mediaR = sumaR / cuenta;
    final mediaG = sumaG / cuenta;
    final mediaB = sumaB / cuenta;
    double desvio(double suma2, double media) =>
        math.sqrt(math.max(0, suma2 / cuenta - media * media));
    final desvioMedio =
        (desvio(sumaR2, mediaR) +
            desvio(sumaG2, mediaG) +
            desvio(sumaB2, mediaB)) /
        3;

    if (desvioMedio <= _desvioDeEstudio) {
      return RasgosDeImagen(
        tipo: TipoDeFoto.estudio,
        proporcion: proporcion,
        tono: tono,
        fondo: Color.fromARGB(
          255,
          mediaR.round(),
          mediaG.round(),
          mediaB.round(),
        ),
      );
    }
    return RasgosDeImagen(
      tipo: TipoDeFoto.escena,
      proporcion: proporcion,
      tono: tono,
    );
  }

  /// Promedio del tercio central, ignorando lo transparente.
  static Color _tonoDelCentro(Uint8List rgba, int ancho, int alto) {
    var r = 0.0, g = 0.0, b = 0.0, n = 0;
    for (var y = alto ~/ 3; y < alto * 2 ~/ 3; y++) {
      for (var x = ancho ~/ 3; x < ancho * 2 ~/ 3; x++) {
        final i = (y * ancho + x) * 4;
        if (rgba[i + 3] < 24) continue;
        r += rgba[i];
        g += rgba[i + 1];
        b += rgba[i + 2];
        n++;
      }
    }
    if (n == 0) return RasgosDeImagen.ausente.tono;
    return Color.fromARGB(255, (r / n).round(), (g / n).round(), (b / n).round());
  }
}
