import 'package:flutter/material.dart';

import 'paleta.dart';

/// Las tres familias de la marca y la escala de textos.
///
/// Inter para leer, Space Grotesk para titular y para los precios, JetBrains
/// Mono para los rótulos «de código». Las tres están declaradas en el
/// `pubspec.yaml`; en los tests no se cargan y Flutter cae a su fuente de
/// prueba, que es lo esperable.
abstract final class Tipografia {
  static const texto = 'Inter';
  static const titulares = 'SpaceGrotesk';

  /// Sin el signo ₲: nunca se usa para un importe.
  static const mono = 'JetBrainsMono';

  /// Cifras de ancho fijo: una columna de montos no baila al cambiar un dígito.
  static const cifrasTabulares = [FontFeature.tabularFigures()];

  static TextTheme escala() {
    const titular = TextStyle(
      fontFamily: titulares,
      color: Paleta.texto,
      fontWeight: FontWeight.w700,
    );
    const lectura = TextStyle(
      fontFamily: texto,
      color: Paleta.texto,
    );
    return TextTheme(
      displayLarge: titular.copyWith(
        fontSize: 52,
        height: 1.02,
        letterSpacing: -1.6,
      ),
      displayMedium: titular.copyWith(
        fontSize: 44,
        height: 1.04,
        letterSpacing: -1.3,
      ),
      displaySmall: titular.copyWith(
        fontSize: 36,
        height: 1.06,
        letterSpacing: -0.9,
      ),
      headlineLarge: titular.copyWith(
        fontSize: 32,
        height: 1.1,
        letterSpacing: -0.7,
      ),
      headlineMedium: titular.copyWith(
        fontSize: 28,
        height: 1.12,
        letterSpacing: -0.5,
      ),
      headlineSmall: titular.copyWith(
        fontSize: 24,
        height: 1.15,
        fontWeight: FontWeight.w600,
        letterSpacing: -0.3,
      ),
      titleLarge: titular.copyWith(
        fontSize: 21,
        height: 1.2,
        fontWeight: FontWeight.w600,
        letterSpacing: -0.2,
      ),
      titleMedium: lectura.copyWith(
        fontSize: 16,
        height: 1.35,
        fontWeight: FontWeight.w600,
      ),
      titleSmall: lectura.copyWith(
        fontSize: 14,
        height: 1.35,
        fontWeight: FontWeight.w600,
      ),
      bodyLarge: lectura.copyWith(fontSize: 16, height: 1.5),
      bodyMedium: lectura.copyWith(fontSize: 14, height: 1.45),
      bodySmall: lectura.copyWith(
        fontSize: 12.5,
        height: 1.4,
        color: Paleta.textoSecundario,
      ),
      labelLarge: lectura.copyWith(
        fontSize: 14.5,
        height: 1.2,
        fontWeight: FontWeight.w600,
        letterSpacing: 0.1,
      ),
      labelMedium: lectura.copyWith(
        fontSize: 12.5,
        height: 1.2,
        fontWeight: FontWeight.w600,
        letterSpacing: 0.2,
      ),
      labelSmall: lectura.copyWith(
        fontSize: 11,
        height: 1.2,
        fontWeight: FontWeight.w600,
        letterSpacing: 0.3,
      ),
    );
  }
}
