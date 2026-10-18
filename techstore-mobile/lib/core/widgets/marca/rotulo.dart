import 'package:flutter/material.dart';

import '../../diseno/marca_tema.dart';

/// El rótulo comentado: `// recién llegados`.
///
/// Es el `//` del isotipo llevado al texto, y anuncia un bloque. Va en
/// minúscula y uno por bloque: dos seguidos ya no se leen como comentario sino
/// como ruido. El lector de pantalla dice solo el texto, sin las barras.
class Rotulo extends StatelessWidget {
  const Rotulo(this.texto, {this.color, super.key});

  final String texto;

  /// Para teñir el texto con el color de un estado (promoción, aviso).
  final Color? color;

  @override
  Widget build(BuildContext context) {
    final marca = MarcaTema.of(context);
    return Semantics(
      header: true,
      label: texto,
      excludeSemantics: true,
      child: Text.rich(
        TextSpan(
          children: [
            TextSpan(
              text: '// ',
              style: TextStyle(color: marca.cursor),
            ),
            TextSpan(text: texto.toLowerCase()),
          ],
        ),
        style: marca.rotulo.copyWith(color: color),
        maxLines: 1,
        overflow: TextOverflow.ellipsis,
      ),
    );
  }
}
