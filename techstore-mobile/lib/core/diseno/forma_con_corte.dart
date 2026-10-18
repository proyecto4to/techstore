import 'dart:math' as math;

import 'package:flutter/material.dart';

import 'medidas.dart';

/// Rectángulo con la esquina superior izquierda cortada en el ángulo del
/// isotipo (32°) y las otras tres redondeadas.
///
/// Es el gesto de la marca fuera del logo: va en las etiquetas de precio y en
/// las tarjetas destacadas. No en botones ni campos, que tienen que verse como
/// controles y no como piezas de la vidriera.
class FormaConCorte extends OutlinedBorder {
  const FormaConCorte({
    this.corte = 12,
    this.radio = Medidas.radioTarjeta,
    super.side,
  });

  /// Alto del corte. El ancho sale del ángulo: tan(32°) ≈ 0,625, así que el
  /// corte avanza 1,6 veces lo que baja.
  final double corte;
  final double radio;

  static const _avancePorAlto = 1.6;

  Path _camino(Rect r) {
    final c = math.min(corte, r.height / 2);
    final ancho = math.min(c * _avancePorAlto, r.width / 2);
    final rr = math.min(radio, math.min(r.width, r.height) / 2);
    return Path()
      ..moveTo(r.left, r.top + c)
      ..lineTo(r.left + ancho, r.top)
      ..lineTo(r.right - rr, r.top)
      ..arcToPoint(Offset(r.right, r.top + rr), radius: Radius.circular(rr))
      ..lineTo(r.right, r.bottom - rr)
      ..arcToPoint(Offset(r.right - rr, r.bottom), radius: Radius.circular(rr))
      ..lineTo(r.left + rr, r.bottom)
      ..arcToPoint(Offset(r.left, r.bottom - rr), radius: Radius.circular(rr))
      ..close();
  }

  @override
  EdgeInsetsGeometry get dimensions => EdgeInsets.all(side.strokeInset);

  @override
  Path getInnerPath(Rect rect, {TextDirection? textDirection}) =>
      _camino(rect.deflate(side.strokeInset));

  @override
  Path getOuterPath(Rect rect, {TextDirection? textDirection}) =>
      _camino(rect);

  @override
  void paint(Canvas canvas, Rect rect, {TextDirection? textDirection}) {
    if (side.style == BorderStyle.none || side.width == 0) return;
    canvas.drawPath(
      _camino(rect.deflate(side.width / 2)),
      side.toPaint()..strokeJoin = StrokeJoin.round,
    );
  }

  @override
  FormaConCorte copyWith({BorderSide? side, double? corte, double? radio}) =>
      FormaConCorte(
        side: side ?? this.side,
        corte: corte ?? this.corte,
        radio: radio ?? this.radio,
      );

  @override
  ShapeBorder scale(double t) => FormaConCorte(
    side: side.scale(t),
    corte: corte * t,
    radio: radio * t,
  );

  @override
  ShapeBorder? lerpFrom(ShapeBorder? a, double t) {
    if (a is FormaConCorte) {
      return FormaConCorte(
        side: BorderSide.lerp(a.side, side, t),
        corte: _mezclar(a.corte, corte, t),
        radio: _mezclar(a.radio, radio, t),
      );
    }
    return super.lerpFrom(a, t);
  }

  @override
  ShapeBorder? lerpTo(ShapeBorder? b, double t) {
    if (b is FormaConCorte) {
      return FormaConCorte(
        side: BorderSide.lerp(side, b.side, t),
        corte: _mezclar(corte, b.corte, t),
        radio: _mezclar(radio, b.radio, t),
      );
    }
    return super.lerpTo(b, t);
  }

  static double _mezclar(double a, double b, double t) => a + (b - a) * t;

  @override
  bool operator ==(Object other) =>
      other is FormaConCorte &&
      other.side == side &&
      other.corte == corte &&
      other.radio == radio;

  @override
  int get hashCode => Object.hash(side, corte, radio);
}
