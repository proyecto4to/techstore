/// Entrada de montos con separador de miles, en un solo lugar.
///
/// Cada pantalla que pedía plata usaba un `TextField` numérico pelado: quien
/// cargaba 1500000 no tenía forma de ver si le sobraba o le faltaba un cero.
/// Acá el número se agrupa mientras se escribe y el valor que sale para el
/// servidor sigue siendo el número, nunca el texto con puntos.
///
/// Convención paraguaya: el punto agrupa los miles y la coma separa los
/// decimales. El punto se ignora al leer lo tecleado justamente porque es
/// separador de grupo; para decimales se escribe coma.
library;

import 'package:flutter/services.dart';

/// El punto separa miles y la coma decimales.
const String separadorMiles = '.';
const String separadorDecimal = ',';

/// Agrupa de a tres desde la derecha: 1234567 -> 1.234.567
String agruparMiles(String digitos) {
  if (digitos.length <= 3) return digitos;
  final salida = StringBuffer();
  for (var i = 0; i < digitos.length; i++) {
    final desdeLaDerecha = digitos.length - i;
    salida.write(digitos[i]);
    if (desdeLaDerecha > 1 && desdeLaDerecha % 3 == 1) {
      salida.write(separadorMiles);
    }
  }
  return salida.toString();
}

/// Deja solo lo que puede formar un número: dígitos y una coma decimal.
///
/// Los puntos se descartan porque son los separadores que pone el propio
/// formateador; si se los interpretara como coma, reescribir un monto ya
/// agrupado lo convertiría en un decimal absurdo.
String _limpiar(String texto, int decimales) {
  final salida = StringBuffer();
  var vioDecimal = false;
  var escritosDecimales = 0;
  for (final unidad in texto.split('')) {
    final esDigito =
        unidad.codeUnitAt(0) >= 0x30 && unidad.codeUnitAt(0) <= 0x39;
    if (esDigito) {
      if (vioDecimal) {
        if (escritosDecimales >= decimales) continue;
        escritosDecimales++;
      }
      salida.write(unidad);
    } else if (unidad == separadorDecimal) {
      if (decimales == 0) {
        // En un campo sin decimales, pegar los dígitos de después de la coma
        // multiplicaría el monto: «1000,50» terminaba valiendo 100.050. Lo que
        // sigue a la coma se descarta.
        break;
      }
      if (vioDecimal) continue;
      vioDecimal = true;
      salida.write(separadorDecimal);
    }
  }
  return salida.toString();
}

/// El texto ya formateado para mostrar, a partir de lo que se tecleó.
String formatearEntradaMonetaria(String texto, {int decimales = 0}) {
  final limpio = _limpiar(texto, decimales);
  if (limpio.isEmpty) return '';
  final partes = limpio.split(separadorDecimal);
  // Sin dígitos enteros la coma sola no significa nada todavía.
  final enteros = agruparMiles(partes.first);
  if (partes.length == 1) return enteros;
  return '${enteros.isEmpty ? '0' : enteros}$separadorDecimal${partes[1]}';
}

/// El número que va al servidor, o `null` si no hay ninguno.
///
/// Acepta tanto el texto crudo como el ya formateado, así da igual si lo llama
/// el formulario o algo que releyó el campo.
double? parsearMonto(String? texto, {int decimales = 0}) {
  if (texto == null) return null;
  final limpio = _limpiar(texto, decimales);
  if (limpio.isEmpty || limpio == separadorDecimal) return null;
  final normalizado = limpio.replaceFirst(separadorDecimal, '.');
  return double.tryParse(normalizado);
}

/// Aplica el formato mientras se escribe, sin que el cursor salte al final.
final class MoneyInputFormatter extends TextInputFormatter {
  const MoneyInputFormatter({this.decimales = 0});

  /// Cuántos decimales admite el campo. El guaraní no usa centavos, así que
  /// por omisión es cero.
  final int decimales;

  @override
  TextEditingValue formatEditUpdate(
    TextEditingValue anterior,
    TextEditingValue nuevo,
  ) {
    final formateado = formatearEntradaMonetaria(
      nuevo.text,
      decimales: decimales,
    );
    if (formateado.isEmpty) {
      return const TextEditingValue();
    }
    // El cursor se reubica contando dígitos desde el final y no posiciones
    // absolutas: al insertarse un separador, la posición absoluta se corre.
    final cursor = nuevo.selection.end.clamp(0, nuevo.text.length);
    final digitosDespues = _contarDigitos(nuevo.text.substring(cursor));
    var posicion = formateado.length;
    var contados = 0;
    while (posicion > 0 && contados < digitosDespues) {
      posicion--;
      if (_esDigito(formateado[posicion])) contados++;
    }
    return TextEditingValue(
      text: formateado,
      selection: TextSelection.collapsed(offset: posicion),
    );
  }

  static bool _esDigito(String unidad) =>
      unidad.codeUnitAt(0) >= 0x30 && unidad.codeUnitAt(0) <= 0x39;

  static int _contarDigitos(String texto) =>
      texto.split('').where(_esDigito).length;
}
