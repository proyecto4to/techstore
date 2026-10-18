import 'package:flutter/widgets.dart';

/// Espaciado sobre una grilla de 4 y los radios de la marca.
abstract final class Medidas {
  static const xs = 4.0;
  static const sm = 8.0;
  static const md = 12.0;
  static const lg = 16.0;
  static const xl = 20.0;
  static const xxl = 28.0;
  static const bloque = 36.0;

  /// Margen lateral de las pantallas.
  static const margen = 20.0;

  static const radioChico = 10.0;
  static const radioControl = 14.0;
  static const radioTarjeta = 20.0;
  static const radioHoja = 28.0;

  /// Lo mínimo que tiene que medir algo tocable. Debajo de esto el dedo falla.
  static const toque = 48.0;

  static const bordeControl = BorderRadius.all(Radius.circular(radioControl));
  static const bordeTarjeta = BorderRadius.all(Radius.circular(radioTarjeta));
}
