import 'package:flutter/material.dart';

import '../../diseno/paleta.dart';
import '../../diseno/tipografia.dart';
import 'isotipo.dart';

/// «TechStore» en dos tonos, como lo escribe el manual: «Tech» en el color del
/// texto y «Store» en azul.
class Logotipo extends StatelessWidget {
  const Logotipo({this.tamano = 22, super.key});

  final double tamano;

  @override
  Widget build(BuildContext context) => Text.rich(
    TextSpan(
      children: const [
        TextSpan(text: 'Tech'),
        TextSpan(
          text: 'Store',
          style: TextStyle(color: Paleta.azulLuminoso),
        ),
      ],
      style: TextStyle(
        fontFamily: Tipografia.titulares,
        fontWeight: FontWeight.w700,
        fontSize: tamano,
        height: 1,
        letterSpacing: -0.02 * tamano,
        color: Paleta.texto,
      ),
    ),
    semanticsLabel: 'TechStore',
    maxLines: 1,
    softWrap: false,
  );
}

/// Isotipo y nombre en línea, para las barras.
class Imagotipo extends StatelessWidget {
  const Imagotipo({this.tamano = 20, super.key});

  /// Tamaño de la letra del nombre; el isotipo se ajusta a la mayúscula.
  final double tamano;

  @override
  Widget build(BuildContext context) => MergeSemantics(
    child: Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        ExcludeSemantics(child: Isotipo(ancho: tamano * 1.05)),
        SizedBox(width: tamano * 0.36),
        Logotipo(tamano: tamano),
      ],
    ),
  );
}
