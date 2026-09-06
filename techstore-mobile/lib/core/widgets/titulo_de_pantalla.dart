import 'package:flutter/material.dart';

/// El título de una pantalla, que se achica cuando no entra.
///
/// Un `Text` común en la barra superior se corta con puntos suspensivos, y con
/// la letra grande que muchos equipos traen configurada eso pasa seguido:
/// «Ubicación de la empresa» se leía «Ubicación de la empr…». Perder el final
/// de un título es perder justo la parte que lo distingue de otro parecido.
///
/// `BoxFit.scaleDown` solo reduce: si el texto entra, queda del tamaño normal,
/// así que las pantallas de título corto no cambian en nada.
class TituloDePantalla extends StatelessWidget {
  const TituloDePantalla(this.texto, {super.key});

  final String texto;

  @override
  Widget build(BuildContext context) {
    return FittedBox(
      fit: BoxFit.scaleDown,
      alignment: AlignmentDirectional.centerStart,
      child: Text(texto, maxLines: 1),
    );
  }
}
