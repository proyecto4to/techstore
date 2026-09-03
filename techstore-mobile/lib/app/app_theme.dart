import 'package:flutter/material.dart';

abstract final class AppTheme {
  static const _seed = Color(0xFF2255A4);

  static ThemeData light() => _theme(Brightness.light);

  static ThemeData dark() => _theme(Brightness.dark);

  static ThemeData _theme(Brightness brightness) {
    final scheme = ColorScheme.fromSeed(
      seedColor: _seed,
      brightness: brightness,
    );
    return ThemeData(
      colorScheme: scheme,
      useMaterial3: true,
      inputDecorationTheme: const InputDecorationTheme(
        border: OutlineInputBorder(),
        // Por defecto el error y la ayuda ocupan una sola línea y Flutter las
        // corta con puntos suspensivos. En el teléfono «Escribí un correo
        // válido, como nombre@correo.com» se leía «…como nombre…», que corta
        // justo el ejemplo que resuelve la duda.
        errorMaxLines: 3,
        helperMaxLines: 3,
      ),
      cardTheme: const CardThemeData(margin: EdgeInsets.zero),
    );
  }
}
