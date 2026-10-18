import 'package:flutter/material.dart';

import '../animacion/aparicion.dart';
import '../diseno/medidas.dart';
import '../diseno/paleta.dart';

/// Un acceso directo: ícono en su marco y nombre. Lo usan el inicio de quien
/// trabaja en el local y la sección «Tu cuenta» de la vidriera.
class BaldosaDeAcceso extends StatelessWidget {
  const BaldosaDeAcceso({
    required this.icono,
    required this.etiqueta,
    required this.onTap,
    this.pendientes = 0,
    this.acento = Paleta.azulLuminoso,
    this.compacta = false,
    super.key,
  });

  final IconData icono;
  final String etiqueta;
  final VoidCallback onTap;

  /// Cuántos avisos sin leer anunciar sobre el ícono. Cero no muestra nada.
  final int pendientes;
  final Color acento;

  /// Ícono arriba y nombre centrado abajo, para un carril horizontal.
  final bool compacta;

  @override
  Widget build(BuildContext context) => Semantics(
    // Un nodo propio: el lector de pantalla tiene que leer cada baldosa como
    // un elemento, no mezclada con el carril o la grilla que la contiene.
    container: true,
    button: true,
    // El número va también en la etiqueta hablada: el globo del ícono queda
    // fuera de la semántica y, sin esto, quien usa lector de pantalla no se
    // entera de que hay algo pendiente.
    label: pendientes == 0 ? etiqueta : '$etiqueta, $pendientes sin leer',
    child: Presionable(
      onTap: onTap,
      child: DecoratedBox(
        decoration: const BoxDecoration(
          color: Paleta.negroAzulado,
          borderRadius: Medidas.bordeTarjeta,
          border: Border.fromBorderSide(BorderSide(color: Paleta.borde)),
        ),
        child: Padding(
          padding: compacta
              ? const EdgeInsets.fromLTRB(6, 12, 6, 10)
              : const EdgeInsets.fromLTRB(14, 14, 12, 12),
          child: Column(
            crossAxisAlignment: compacta
                ? CrossAxisAlignment.center
                : CrossAxisAlignment.start,
            children: [
              ExcludeSemantics(
                child: Badge(
                  isLabelVisible: pendientes > 0,
                  label: Text(pendientes > 99 ? '99+' : '$pendientes'),
                  child: Container(
                    width: 42,
                    height: 42,
                    decoration: BoxDecoration(
                      color: acento.withValues(alpha: 0.1),
                      borderRadius: BorderRadius.circular(13),
                      border: Border.all(color: acento.withValues(alpha: 0.3)),
                    ),
                    child: Icon(icono, size: 22, color: acento),
                  ),
                ),
              ),
              const Spacer(),
              // Fuera de la semántica porque el nombre ya está en la etiqueta
              // de la baldosa: sin esto el lector de pantalla lo decía dos
              // veces, «Notificaciones, Notificaciones».
              ExcludeSemantics(
                child: Text(
                  etiqueta,
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                  textAlign: compacta ? TextAlign.center : TextAlign.start,
                  style: compacta
                      ? Theme.of(context).textTheme.labelMedium
                      : Theme.of(context).textTheme.titleSmall,
                ),
              ),
            ],
          ),
        ),
      ),
    ),
  );
}
